#include <jni.h>
#include <android/log.h>
#include <termux_pty.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <pthread.h>

#define LOG_TAG "XcoderTerminal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static pthread_mutex_t sessions_mutex = PTHREAD_MUTEX_INITIALIZER;

static pty_session_t* find_session(long fd) {
    return reinterpret_cast<pty_session_t*>(fd);
}

pty_session_t* pty_create_session(
    const char* shell_path,
    char* const* argv,
    char* const* envp,
    const char* cwd,
    int cols,
    int rows
) {
    pty_session_t* session = (pty_session_t*)malloc(sizeof(pty_session_t));
    if (!session) {
        LOGE("Failed to allocate pty_session_t");
        return NULL;
    }
    session->master_fd = -1;
    session->slave_fd = -1;
    session->child_pid = -1;
    session->exit_status = -1;
    session->is_running = 0;

    int master_fd = posix_openpt(O_RDWR);
    if (master_fd < 0) {
        LOGE("posix_openpt() failed: %s", strerror(errno));
        free(session);
        return NULL;
    }

    if (grantpt(master_fd) < 0) {
        LOGE("grantpt() failed: %s", strerror(errno));
        close(master_fd);
        free(session);
        return NULL;
    }

    if (unlockpt(master_fd) < 0) {
        LOGE("unlockpt() failed: %s", strerror(errno));
        close(master_fd);
        free(session);
        return NULL;
    }

    int slave_fd = open(ptsname(master_fd), O_RDWR);
    if (slave_fd < 0) {
        LOGE("open slave pty failed: %s", strerror(errno));
        close(master_fd);
        free(session);
        return NULL;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (cols > 0) ? cols : 80;
    ws.ws_row = (rows > 0) ? rows : 24;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    if (ioctl(slave_fd, TIOCSWINSIZE, &ws) < 0) {
        LOGW("ioctl TIOCSWINSIZE failed: %s", strerror(errno));
    }

    struct termios tios;
    if (tcgetattr(slave_fd, &tios) == 0) {
        tios.c_iflag |= ICRNL;
        tios.c_iflag &= ~(IXON | IXOFF | INLCR | IGNCR);
        tios.c_oflag |= ONLCR;
        tios.c_oflag &= ~(OCRNL | ONOCR | ONLRET);
        tios.c_lflag |= ICANON | ISIG | IEXTEN | ECHO | ECHOE | ECHOK;
        tios.c_lflag &= ~(ECHOCTL | ECHONL);
        tios.c_cflag |= CS8 | CREAD;
        tios.c_cflag &= ~(PARENB | CSTOPB | CSIZE);
        tios.c_cc[VMIN] = 1;
        tios.c_cc[VTIME] = 0;
        tios.c_cc[VINTR] = 3;   // Ctrl+C
        tios.c_cc[VQUIT] = 28;  // Ctrl+\
        tios.c_cc[VERASE] = 127; // DEL
        tios.c_cc[VKILL] = 21;  // Ctrl+U
        tios.c_cc[VEOF] = 4;    // Ctrl+D
        tios.c_cc[VSTART] = 17; // Ctrl+Q
        tios.c_cc[VSTOP] = 19;  // Ctrl+S
        tios.c_cc[VSUSP] = 26;  // Ctrl+Z
        tcsetattr(slave_fd, TCSANOW, &tios);
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        close(slave_fd);
        close(master_fd);
        free(session);
        return NULL;
    }

    if (pid == 0) {
        // Child process
        close(master_fd);

        // Create new session
        setsid();

        // Set the slave as the controlling terminal
        int saved_slave = slave_fd;
        slave_fd = open(ptsname(saved_slave), O_RDWR);
        close(saved_slave);

        if (slave_fd < 0) {
            _exit(1);
        }

        // Dup slave to stdin, stdout, stderr
        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);
        if (slave_fd > STDERR_FILENO) {
            close(slave_fd);
        }

        // Set controlling terminal
        if (ioctl(STDIN_FILENO, TIOCSCTTY, 0) < 0) {
            LOGW("TIOCSCTTY failed: %s", strerror(errno));
        }

        // Apply terminal settings
        if (tcgetattr(STDIN_FILENO, &tios) == 0) {
            tios.c_iflag |= ICRNL;
            tios.c_iflag &= ~(IXON | IXOFF | INLCR | IGNCR);
            tios.c_oflag |= ONLCR;
            tios.c_oflag &= ~(OCRNL | ONOCR | ONLRET);
            tios.c_lflag |= ICANON | ISIG | IEXTEN | ECHO | ECHOE | ECHOK;
            tios.c_lflag &= ~(ECHOCTL | ECHONL);
            tios.c_cflag |= CS8 | CREAD;
            tios.c_cflag &= ~(PARENB | CSTOPB | CSIZE);
            tios.c_cc[VMIN] = 1;
            tios.c_cc[VTIME] = 0;
            tcsetattr(STDIN_FILENO, TCSANOW, &tios);
        }

        // Change working directory
        if (cwd != NULL && strlen(cwd) > 0) {
            if (chdir(cwd) < 0) {
                LOGW("chdir to %s failed: %s", cwd, strerror(errno));
            }
        }

        // Execute shell
        if (envp != NULL) {
            execvpe(shell_path, argv, envp);
        } else {
            execvp(shell_path, argv);
        }

        // If exec fails
        _exit(127);
    }

    // Parent process
    close(slave_fd);

    session->master_fd = master_fd;
    session->slave_fd = -1; // closed in parent
    session->child_pid = pid;
    session->is_running = 1;

    LOGI("PTY session created: master_fd=%d, child_pid=%d", master_fd, pid);

    return session;
}

int pty_write(pty_session_t* session, const char* data, int len) {
    if (!session || session->master_fd < 0 || !session->is_running) {
        return -1;
    }

    int written = write(session->master_fd, data, len);
    if (written < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }
        LOGE("pty_write failed: %s", strerror(errno));
        return -1;
    }
    return written;
}

int pty_read(pty_session_t* session, char* buffer, int buffer_size) {
    if (!session || session->master_fd < 0 || buffer == NULL || buffer_size <= 0) {
        return -1;
    }

    // Check if child process has exited (non-blocking)
    if (session->is_running) {
        int status = 0;
        pid_t result = waitpid(session->child_pid, &status, WNOHANG);
        if (result == session->child_pid) {
            session->is_running = 0;
            if (WIFEXITED(status)) {
                session->exit_status = WEXITSTATUS(status);
            } else if (WIFSIGNALED(status)) {
                session->exit_status = -WTERMSIG(status);
            } else {
                session->exit_status = 1;
            }
            LOGI("Child process exited with status %d", session->exit_status);
        }
    }

    // If child exited, drain remaining data
    fd_set read_fds;
    FD_ZERO(&read_fds);
    FD_SET(session->master_fd, &read_fds);

    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 50000; // 50ms timeout

    int select_result = select(session->master_fd + 1, &read_fds, NULL, NULL, &tv);
    if (select_result < 0) {
        if (errno == EINTR) {
            return 0;
        }
        LOGE("pty_read select failed: %s", strerror(errno));
        return -1;
    }

    if (select_result == 0) {
        // No data available
        if (!session->is_running) {
            return -1; // Signal EOF
        }
        return 0;
    }

    int bytes_read = read(session->master_fd, buffer, buffer_size);
    if (bytes_read < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) {
            return 0;
        }
        LOGE("pty_read failed: %s", strerror(errno));
        return -1;
    }

    if (bytes_read == 0) {
        return -1; // EOF
    }

    return bytes_read;
}

int pty_resize(pty_session_t* session, int cols, int rows) {
    if (!session || session->master_fd < 0) {
        return -1;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (cols > 0) ? cols : 80;
    ws.ws_row = (rows > 0) ? rows : 24;

    if (ioctl(session->master_fd, TIOCSWINSIZE, &ws) < 0) {
        LOGE("pty_resize failed: %s", strerror(errno));
        return -1;
    }

    // Send SIGWINCH to the child process
    if (session->child_pid > 0 && session->is_running) {
        kill(session->child_pid, SIGWINCH);
    }

    return 0;
}

void pty_close(pty_session_t* session) {
    if (!session) return;

    if (session->master_fd >= 0) {
        close(session->master_fd);
        session->master_fd = -1;
    }

    if (session->child_pid > 0) {
        // Send SIGHUP to the child process group
        kill(-session->child_pid, SIGHUP);

        // Wait briefly for the child to exit
        int status = 0;
        int wait_attempts = 0;
        while (wait_attempts < 10) {
            pid_t result = waitpid(session->child_pid, &status, WNOHANG);
            if (result == session->child_pid) {
                if (WIFEXITED(status)) {
                    session->exit_status = WEXITSTATUS(status);
                }
                break;
            }
            usleep(10000); // 10ms
            wait_attempts++;
        }

        if (wait_attempts >= 10) {
            kill(-session->child_pid, SIGKILL);
            waitpid(session->child_pid, &status, 0);
        }
        session->child_pid = -1;
    }

    session->is_running = 0;
    free(session);
    LOGI("PTY session closed");
}

int pty_get_exit_status(pty_session_t* session) {
    if (!session) return -1;

    if (session->is_running) {
        int status = 0;
        pid_t result = waitpid(session->child_pid, &status, WNOHANG);
        if (result == session->child_pid) {
            session->is_running = 0;
            if (WIFEXITED(status)) {
                session->exit_status = WEXITSTATUS(status);
            } else if (WIFSIGNALED(status)) {
                session->exit_status = -WTERMSIG(status);
            } else {
                session->exit_status = 1;
            }
        }
    }

    return session->exit_status;
}

// ==================== JNI Functions ====================

static jfieldID getFdField(JNIEnv* env, jobject obj) {
    jclass cls = env->GetObjectClass(obj);
    return env->GetFieldID(cls, "ptyFd", "J");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_xcoder_core_terminal_TerminalSession_nativeCreatePty(
    JNIEnv* env,
    jobject thiz,
    jstring shell_path,
    jobjectArray args,
    jobjectArray env_array,
    jstring cwd,
    jint cols,
    jint rows
) {
    const char* shell = env->GetStringUTFChars(shell_path, NULL);
    if (!shell) {
        return -1;
    }

    // Build argv array
    int argc = 1; // at least the shell path
    if (args != NULL) {
        argc += env->GetArrayLength(args);
    }

    char** argv = (char**)malloc((argc + 1) * sizeof(char*));
    argv[0] = strdup(shell);
    int arg_idx = 1;
    if (args != NULL) {
        int args_len = env->GetArrayLength(args);
        for (int i = 0; i < args_len && arg_idx < argc; i++) {
            jstring arg = (jstring)env->GetObjectArrayElement(args, i);
            const char* arg_str = env->GetStringUTFChars(arg, NULL);
            argv[arg_idx++] = strdup(arg_str);
            env->ReleaseStringUTFChars(arg, arg_str);
            env->DeleteLocalRef(arg);
        }
    }
    argv[arg_idx] = NULL;

    // Build envp array
    char** envp = NULL;
    if (env_array != NULL) {
        int env_count = env->GetArrayLength(env_array);
        envp = (char**)malloc((env_count + 1) * sizeof(char*));
        for (int i = 0; i < env_count; i++) {
            jstring env_str = (jstring)env->GetObjectArrayElement(env_array, i);
            const char* env_cstr = env->GetStringUTFChars(env_str, NULL);
            envp[i] = strdup(env_cstr);
            env->ReleaseStringUTFChars(env_str, env_cstr);
            env->DeleteLocalRef(env_str);
        }
        envp[env_count] = NULL;
    }

    const char* cwd_str = NULL;
    if (cwd != NULL) {
        cwd_str = env->GetStringUTFChars(cwd, NULL);
    }

    pty_session_t* session = pty_create_session(shell, argv, envp, cwd_str, cols, rows);

    // Cleanup
    for (int i = 0; argv[i] != NULL; i++) {
        free(argv[i]);
    }
    free(argv);

    if (envp != NULL) {
        for (int i = 0; envp[i] != NULL; i++) {
            free(envp[i]);
        }
        free(envp);
    }

    if (cwd_str != NULL) {
        env->ReleaseStringUTFChars(cwd, cwd_str);
    }
    env->ReleaseStringUTFChars(shell_path, shell);

    return (jlong)session;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_xcoder_core_terminal_TerminalSession_nativeWritePty(
    JNIEnv* env,
    jobject thiz,
    jlong fd,
    jbyteArray data,
    jint len
) {
    pty_session_t* session = find_session(fd);
    if (!session) return -1;

    jbyte* bytes = env->GetByteArrayElements(data, NULL);
    if (!bytes) return -1;

    int written = pty_write(session, (const char*)bytes, len);

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return written;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_xcoder_core_terminal_TerminalSession_nativeReadPty(
    JNIEnv* env,
    jobject thiz,
    jlong fd,
    jbyteArray buffer,
    jint buffer_size
) {
    pty_session_t* session = find_session(fd);
    if (!session) return -1;

    jbyte* buf = env->GetByteArrayElements(buffer, NULL);
    if (!buf) return -1;

    int bytes_read = pty_read(session, (char*)buf, buffer_size);

    if (bytes_read > 0) {
        env->ReleaseByteArrayElements(buffer, buf, 0);
    } else {
        env->ReleaseByteArrayElements(buffer, buf, JNI_ABORT);
    }

    return bytes_read;
}

extern "C" JNIEXPORT void JNICALL
Java_com_xcoder_core_terminal_TerminalSession_nativeResizePty(
    JNIEnv* env,
    jobject thiz,
    jlong fd,
    jint cols,
    jint rows
) {
    pty_session_t* session = find_session(fd);
    if (session) {
        pty_resize(session, cols, rows);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_xcoder_core_terminal_TerminalSession_nativeClosePty(
    JNIEnv* env,
    jobject thiz,
    jlong fd
) {
    pty_session_t* session = find_session(fd);
    if (session) {
        pty_close(session);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_xcoder_core_terminal_TerminalSession_nativeGetExitStatus(
    JNIEnv* env,
    jobject thiz,
    jlong fd
) {
    pty_session_t* session = find_session(fd);
    if (!session) return -1;
    return pty_get_exit_status(session);
}