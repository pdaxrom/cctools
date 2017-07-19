#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <signal.h>
#include <android/log.h>

# include <unistd.h>
# include <utime.h>

#define  LOG_TAG    "cctools-utils"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

extern "C" {

    JNIEXPORT jobject Java_com_pdaxrom_utils_Utils_createSubProcess(JNIEnv *env, jobject clazz,
	jstring dir, jstring cmd, jobjectArray args, jobjectArray envVars,
	jintArray processIdArray);

    JNIEXPORT void Java_com_pdaxrom_utils_Utils_setPtyWindowSize(JNIEnv *env, jobject clazz,
	jobject fileDescriptor, jint row, jint col, jint xpixel, jint ypixel);

    JNIEXPORT void Java_com_pdaxrom_utils_Utils_setPtyUTF8Mode(JNIEnv *env, jobject clazz,
	jobject fileDescriptor, jboolean utf8Mode);

    JNIEXPORT jint Java_com_pdaxrom_utils_Utils_waitFor(JNIEnv *env, jobject clazz,
	jint procId);

    JNIEXPORT jint Java_com_pdaxrom_utils_Utils_readByte(JNIEnv *env, jobject clazz, jobject fileDescriptor);

    JNIEXPORT jint Java_com_pdaxrom_utils_Utils_writeByte(JNIEnv *env, jobject clazz, jobject fileDescriptor, jint byte);

    JNIEXPORT void Java_com_pdaxrom_utils_Utils_close(JNIEnv *env, jobject clazz, jobject fileDescriptor);

    JNIEXPORT void Java_com_pdaxrom_utils_Utils_hangupProcessGroup(JNIEnv *env, jobject clazz,
	jint procId);

}

static jfieldID field_fileDescriptor_descriptor;

typedef unsigned short char16_t;

class String8 {
public:
    String8() {
        mString = 0;
    }

    ~String8() {
        if (mString) {
            free(mString);
        }
    }

    void set(const char16_t* o, size_t numChars) {
        if (mString) {
            free(mString);
        }
        mString = (char*) malloc(numChars + 1);
        if (!mString) {
            return;
        }
        for (size_t i = 0; i < numChars; i++) {
            mString[i] = (char) o[i];
        }
        mString[numChars] = '\0';
    }

    const char* string() {
        return mString;
    }
private:
    char* mString;
};

static int throwOutOfMemoryError(JNIEnv *env, const char *message)
{
    jclass exClass;
    const char *className = "java/lang/OutOfMemoryError";

    exClass = env->FindClass(className);
    return env->ThrowNew(exClass, message);
}

static int create_subprocess(const char *dir, const char *cmd,
    char *const argv[], char *const envp[], int* pProcessId)
{
    char *devname;
    int ptm;
    pid_t pid;

    ptm = open("/dev/ptmx", O_RDWR); // | O_NOCTTY);
    if(ptm < 0){
        LOGE("[ cannot open /dev/ptmx - %s ]\n",strerror(errno));
        return -1;
    }
    fcntl(ptm, F_SETFD, FD_CLOEXEC);

    if(grantpt(ptm) || unlockpt(ptm) ||
       ((devname = (char*) ptsname(ptm)) == 0)){
        LOGE("[ trouble with /dev/ptmx - %s ]\n", strerror(errno));
        return -1;
    }

    pid = fork();
    if(pid < 0) {
        LOGE("- fork failed: %s -\n", strerror(errno));
        return -1;
    }

    if(pid == 0){
        int pts;

        setsid();

        chdir(dir);

        pts = open(devname, O_RDWR);
        if(pts < 0) exit(-1);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        if (envp) {
            for (; *envp; ++envp) {
                putenv(*envp);
            }
        }

        close(ptm);

        execv(cmd, argv);
        exit(-1);
    } else {
        *pProcessId = (int) pid;
        return ptm;
    }
}

JNIEXPORT jobject Java_com_pdaxrom_utils_Utils_createSubProcess(JNIEnv *env, jobject clazz,
    jstring dir, jstring cmd, jobjectArray args, jobjectArray envVars,
    jintArray processIdArray)
{
    const jchar* str = cmd ? env->GetStringCritical(cmd, 0) : 0;
    String8 cmd_8;
    if (str) {
        cmd_8.set(str, env->GetStringLength(cmd));
        env->ReleaseStringCritical(cmd, str);
    }

    str = dir ? env->GetStringCritical(dir, 0) : 0;
    String8 dir_8;
    if (str) {
        dir_8.set(str, env->GetStringLength(dir));
        env->ReleaseStringCritical(dir, str);
    }

    LOGI("dir=%s cmd=%s", dir_8.string(), cmd_8.string());

    jsize size = args ? env->GetArrayLength(args) : 0;
    char **argv = NULL;
    String8 tmp_8;
    if (size > 0) {
        argv = (char **)malloc((size+1)*sizeof(char *));
        if (!argv) {
            throwOutOfMemoryError(env, "Couldn't allocate argv array");
            return NULL;
        }
        for (int i = 0; i < size; ++i) {
            jstring arg = reinterpret_cast<jstring>(env->GetObjectArrayElement(args, i));
            str = env->GetStringCritical(arg, 0);
            if (!str) {
                throwOutOfMemoryError(env, "Couldn't get argument from array");
                return NULL;
            }
            tmp_8.set(str, env->GetStringLength(arg));
            env->ReleaseStringCritical(arg, str);
            argv[i] = strdup(tmp_8.string());
        }
        argv[size] = NULL;
    }

    size = envVars ? env->GetArrayLength(envVars) : 0;
    char **envp = NULL;
    if (size > 0) {
        envp = (char **)malloc((size+1)*sizeof(char *));
        if (!envp) {
            throwOutOfMemoryError(env, "Couldn't allocate envp array");
            return NULL;
        }
        for (int i = 0; i < size; ++i) {
            jstring var = reinterpret_cast<jstring>(env->GetObjectArrayElement(envVars, i));
            str = env->GetStringCritical(var, 0);
            if (!str) {
                throwOutOfMemoryError(env, "Couldn't get env var from array");
                return NULL;
            }
            tmp_8.set(str, env->GetStringLength(var));
            env->ReleaseStringCritical(var, str);
            envp[i] = strdup(tmp_8.string());
        }
        envp[size] = NULL;
    }

    int procId = -1;
    int ptm = create_subprocess(dir_8.string(), cmd_8.string(), argv, envp, &procId);

    if (argv) {
        for (char **tmp = argv; *tmp; ++tmp) {
            free(*tmp);
        }
        free(argv);
    }
    if (envp) {
        for (char **tmp = envp; *tmp; ++tmp) {
            free(*tmp);
        }
        free(envp);
    }

    if (processIdArray) {
        int procIdLen = env->GetArrayLength(processIdArray);
        if (procIdLen > 0) {
            jboolean isCopy;

            int* pProcId = (int*) env->GetPrimitiveArrayCritical(processIdArray, &isCopy);
            if (pProcId) {
                *pProcId = procId;
                env->ReleasePrimitiveArrayCritical(processIdArray, pProcId, 0);
            }
        }
    }

    jclass class_fileDescriptor = env->FindClass("java/io/FileDescriptor");
    jmethodID method_fileDescriptor_init = env->GetMethodID(class_fileDescriptor, "<init>", "()V");

    jobject result = env->NewObject(class_fileDescriptor, method_fileDescriptor_init);

    if (!result) {
        LOGE("Couldn't create a FileDescriptor.");
    }
    else {
        field_fileDescriptor_descriptor = env->GetFieldID(class_fileDescriptor, "descriptor", "I");
        env->SetIntField(result, field_fileDescriptor_descriptor, ptm);
    }

    return result;
}

JNIEXPORT void Java_com_pdaxrom_utils_Utils_setPtyWindowSize(JNIEnv *env, jobject clazz,
    jobject fileDescriptor, jint row, jint col, jint xpixel, jint ypixel)
{
    int fd;
    struct winsize sz;

    fd = env->GetIntField(fileDescriptor, field_fileDescriptor_descriptor);

    if (env->ExceptionOccurred() != NULL) {
        return;
    }

    sz.ws_row = row;
    sz.ws_col = col;
    sz.ws_xpixel = xpixel;
    sz.ws_ypixel = ypixel;

    ioctl(fd, TIOCSWINSZ, &sz);
}

JNIEXPORT void Java_com_pdaxrom_utils_Utils_setPtyUTF8Mode(JNIEnv *env, jobject clazz,
    jobject fileDescriptor, jboolean utf8Mode)
{
    int fd;
    struct termios tios;

    fd = env->GetIntField(fileDescriptor, field_fileDescriptor_descriptor);

    if (env->ExceptionOccurred() != NULL) {
        return;
    }

    tcgetattr(fd, &tios);
    if (utf8Mode) {
        tios.c_iflag |= IUTF8;
    } else {
        tios.c_iflag &= ~IUTF8;
    }
    tcsetattr(fd, TCSANOW, &tios);
}

JNIEXPORT jint Java_com_pdaxrom_utils_Utils_waitFor(JNIEnv *env, jobject clazz,
    jint procId)
{
    int status;

    LOGI("waitFor %d", procId);

    waitpid(procId, &status, 0);
    int result = 0;
    if (WIFEXITED(status)) {
        result = WEXITSTATUS(status);
    }
    return result;
}

JNIEXPORT jint Java_com_pdaxrom_utils_Utils_readByte(JNIEnv *env, jobject clazz, jobject fileDescriptor)
{
    int fd;

    fd = env->GetIntField(fileDescriptor, field_fileDescriptor_descriptor);

    if (env->ExceptionOccurred() != NULL) {
        return -1;
    }

    char ch;

    int l = read(fd, &ch, 1);

    if (l != 1) {
	return -1;
    }

    return ch;
}

JNIEXPORT jint Java_com_pdaxrom_utils_Utils_writeByte(JNIEnv *env, jobject clazz, jobject fileDescriptor, jint byte)
{
    int fd;

    fd = env->GetIntField(fileDescriptor, field_fileDescriptor_descriptor);

    if (env->ExceptionOccurred() != NULL) {
        return -1;
    }

    char ch = byte & 0xff;

    int l = write(fd, &ch, 1);

    return (l != 1) ? -1: 1;
}

JNIEXPORT void Java_com_pdaxrom_utils_Utils_close(JNIEnv *env, jobject clazz, jobject fileDescriptor)
{
    int fd;

    LOGI("close file descriptor");

    fd = env->GetIntField(fileDescriptor, field_fileDescriptor_descriptor);

    if (env->ExceptionOccurred() != NULL) {
        return;
    }

    close(fd);
}

JNIEXPORT void Java_com_pdaxrom_utils_Utils_hangupProcessGroup(JNIEnv *env, jobject clazz,
    jint procId)
{
    kill(-procId, SIGHUP);
}
