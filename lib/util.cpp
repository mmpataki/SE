#include "util.hpp"
#include <ctype.h>
#include <string>

using namespace std;

string ushort2str(ushort i) {
	char buf[16];
	sprintf(buf, "%d", i);
	return string(buf);
}

void cstoupper(char *str) {
	while(*str) {
		*str = toupper(*str);
		str++;
	}
}