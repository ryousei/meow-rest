#include <stdio.h>
#include <errno.h>
#include <strings.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>

#define DEBUG 1
#define PORT 54894
#define HEAD_SIZE 4
#define B_HEAD_SIZE HEAD_SIZE * 2
#define CONTROL_SIZE 8 // Request, Response size
#define B_CONTROL_SIZE CONTROL_SIZE * 2

int main(int argc, char **argv)
{
  int sd, ad, len;
  struct sockaddr_in saddr, caddr;
  uint16_t head[HEAD_SIZE];
  int i, j;

  char *b = (char *) head;
  int port = PORT;
  int yes=1;
  int flag = 1; 

  if (argc > 1) {
    port = atoi(argv[1]);
  }
  printf("port is %d.\n", port);

  if ((sd = socket(AF_INET, SOCK_STREAM, 0)) == -1) {
    perror("socket");
    exit(1);
  }
  int ret = setsockopt(sd, SOL_SOCKET, SO_REUSEADDR, (const char *)&yes, sizeof(yes));

  memset((char*)&saddr, 0, sizeof(saddr));
  saddr.sin_family      = AF_INET;
  saddr.sin_addr.s_addr = INADDR_ANY;
  saddr.sin_port        = htons(port);

  if (bind(sd, (struct sockaddr*)&saddr, sizeof(saddr)) == -1) {
    perror("bind");
    exit(1);
  }

  if (listen(sd, 5) == -1) {
    perror("listen");
    exit(1);
  }

  while (1) {
    len = sizeof(caddr);
    bzero(&caddr, len);

    printf("wait accept()\n");
    if ((ad = accept(sd, (struct sockaddr *)&caddr, &len)) < 0) {
      perror("accept()");
      exit(1);
    }
    setsockopt(ad, IPPROTO_TCP, TCP_NODELAY, (char *) &flag, sizeof(int));
    printf("accept() now\n");

    int hlen = 0;
    while (ad != 0) {
      printf("wait read\n");
      b = (char *) head;
      bzero((void *)b, B_HEAD_SIZE);

      hlen = 0;
      while (hlen < B_HEAD_SIZE) {
	errno = 0;
	int r = read(ad, b + hlen, B_HEAD_SIZE - hlen);
	printf("read head: bytes = %d\n", r);

	if (r == 0) { /* EOF */
	  printf("read() EOF.\n");
	  close(ad);
	  ad = 0;
	  break;
	}
	if (r < 0) { /* error */
	  // perror("read() error ");
	  printf("read() error: %s.\n", strerror(errno));
	  close(ad);
	  ad = 0;
	  break;
	}
	hlen += r;
      }

      if (ad == 0) break; // create new listen socket
      if (hlen != B_HEAD_SIZE) {
	printf("read: error(read length %d != %d)\n", hlen, B_HEAD_SIZE);
	if (ad != 0) {
	  close(ad);
	  ad = 0;
	}
	break; // exit from read loop.
      } 

      // header is read done now.
      if (DEBUG) printf("read head done: %d\n", hlen);

      for (i = 0; i < HEAD_SIZE; i++) {
	head[i] = (uint16_t) ntohs(head[i]);
      }

      if (DEBUG) {
	printf("head:version = %d,%d,%d,%d.\n", head[0],head[1],head[2],head[3]);
      }

      // read request (control parameters)
      int controls = head[1];
      uint16_t control[controls][CONTROL_SIZE];
      uint16_t response[controls][CONTROL_SIZE];
      bzero((char *)response, controls * B_CONTROL_SIZE);

      for (i = 0; i < controls; i ++) {
	b = (char *) control[i];
	int clen = 0;
	while (clen < B_CONTROL_SIZE) {
	  errno = 0;
	  int r = read(ad, b + clen, B_CONTROL_SIZE - clen);
	  printf("read control: bytes = %d, remain = %d\n", 
		 r, B_CONTROL_SIZE - clen);
	  
	  if (r == 0) { /* EOF */
	    printf("read() EOF.\n");
	    close(ad);
	    ad = 0;
	    break;
	  }
	  if (r < 0) { /* error */
	    // perror("read() error ");
	    printf("read() error: %s.\n", strerror(errno));
	    close(ad);
	    ad = 0;
	    break;
	  }
	  clen += r;
	}
	if (ad == 0) break; // create new listen socket
      }
      if (ad == 0) break; // create new listen socket
      
      // controls are read done.
      for (i = 0; i < controls; i++) {
	for (j = 0; j < CONTROL_SIZE; j++) {
	  control[i][j] = (uint16_t) ntohs(control[i][j]);
	}
      }

      // paths are controlled by EtherCAT.
      // Response header is reuses Request header.

      for (i = 0; i < controls; i++) {

	if (DEBUG) {
	  printf("control %d:.\n", i);
	  printf("\tParamterID\t= %d\n", control[i][0]);
	  printf("\tType\t\t= %d (1:Wave, 2:optical SW)\n", control[i][1]);
	  printf("\tDeviceID\t= %x\n", control[i][2]);
	  printf("\tCommand\t\t= %x (1:setup, 2:teardown, 3:getStatus)\n", control[i][3]);
	  printf("\tChannel/dstPort\t= %x\n", control[i][4]);
	  printf("\tsrcPort\t\t= %x\n", control[i][5]);
	  printf("\tOption#1\t= %x\n", control[i][6]);
	  printf("\tOption#2\t= %x\n", control[i][7]);
	}

	//
	// call EtherCAT and return uint16_t result[CONTORL_SIZE].
	// CONTROL_SIZE(REQUEST_SIZE) == RESPONSE_SIZE.
	// 

	// create response
	response[i][0] = control[i][0];
	response[i][1] = 0; // 0: Success, 1: Timeout, 2: ...
	// for (j = 2; j < CONTROL_SIZE; j++) {
	// }
	if (control[i][3] == 3) {
	  response[i][2] = 0xF0F0;
	  response[i][3] = 0xF1F1;
	  response[i][4] = 0xF2F2;
	  response[i][5] = 0xF3F3;
	  response[i][6] = 0xF4F4;
	  response[i][7] = 0xF5F5;
	} else {
	  response[i][2] = 0x0000;
	  response[i][3] = 0x0000;
	  response[i][4] = 0x0000;
	  response[i][5] = 0x0000;
	  response[i][6] = 0x0000;
	  response[i][7] = 0x0000;
	}
      }

      if (DEBUG) {
	printf("head:version = %d,%d,%d,%d.\n", head[0],head[1],head[2],head[3]);
      }

      for (i = 0; i < HEAD_SIZE; i++) {
	head[i] = (uint16_t) htons(head[i]);
      }
      hlen = write(ad, head, B_HEAD_SIZE);
      printf("write response head. len=%d\n", hlen);

      for (i = 0; i < controls; i++) {
	if (DEBUG) {
	  printf("response %d:.\n", i);
	  printf("\tParamterID = %d\n", response[i][0]);
	  printf("\tErrorCode = %d (0: Success, 1: Timeout, 2:...\n", response[i][1]);
	  printf("\tStatus #1 = %x\n", response[i][2]);
	  printf("\tStatus #2 = %x\n", response[i][3]);
	  printf("\tStatus #3 = %x\n", response[i][4]);
	  printf("\tStatus #4 = %x\n", response[i][5]);
	  printf("\tStatus #5 = %x\n", response[i][6]);
	  printf("\tStatus #6 = %x\n", response[i][7]);
	}

	for (j = 0; j < CONTROL_SIZE; j++) {
	  response[i][j] = (uint16_t) htons(response[i][j]);
	}
	// sleep(2);
	int clen = 0;
	clen = write(ad, response[i], B_CONTROL_SIZE);
	// clen = write(ad, response[i], 8);
	printf("write response. clen=%d\n", clen);
	// sleep(2);
	// clen = write(ad, response[i]+8, 8);
	// printf("write response. clen=%d\n", clen);
      }
    }
  }
}
