#ifndef XCODER_PTY_H
#define XCODER_PTY_H

#include <sys/types.h>
#include <unistd.h>
#include <termios.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int master_fd;
    int slave_fd;
    pid_t child_pid;
    int exit_status;
    int is_running;
} pty_session_t;

pty_session_t* pty_create_session(
    const char* shell_path,
    char* const* argv,
    char* const* envp,
    const char* cwd,
    int cols,
    int rows
);

int pty_write(pty_session_t* session, const char* data, int len);

int pty_read(pty_session_t* session, char* buffer, int buffer_size);

int pty_resize(pty_session_t* session, int cols, int rows);

void pty_close(pty_session_t* session);

int pty_get_exit_status(pty_session_t* session);

#ifdef __cplusplus
}
#endif

#endif // XCODER_PTY_H
