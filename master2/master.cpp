#include "json.hpp"
using json = nlohmann::json;

#include <thread>
#include "masterconfig.hpp"
#include "server.hpp"
#include "proto/phashes.hpp"
#include "proto/pdu.hpp"
#include "reqhandler.hpp"

void handle_request(Socket *s);

class Master : public ReqHandler
{
public:
	MasterConfig *config;
	Server *mserver;
	
	Master(string host, string port) : ReqHandler() {
		config = new MasterConfig(host, port);
		mserver = new Server(
				config->getHost(),
				stoi(config->getPort()),
				this
			);
	}
	
	void run() {
		mserver->run();
	}

	void handle_connect(Socket *s, PDU &pdu) {
		/* assuming whoever is connecting is fireup. */
		config->addSlave(new SlaveConfig(
			pdu.getSenderIp(), pdu.getSenderPort(), "fireup"));

		/* send back the ACK with some random PID */
		PDU p(config->getHost(), config->getPort(), 
			pdu.getSenderIp(), pdu.getSenderPort(),
			METHOD_ACK);
		json j;
		j["PID"] = config->getSlaveCount();
		p.setData(j.dump());
		s->writeData(p.toString());
	}

	void reportStatus(Socket *s) {
		s->writeData("I am fine friend.");
	}

	void handle_get(Socket *s, PDU &pdu) {
		json jdata = json::parse(pdu.getData());
		string resource = jdata["resource"].get<string>();

		if(resource == "status") {
			reportStatus(s);
		} else if(resource == "config") {
			s->writeData(config->toString());
		}
	}

	void handle_update(Socket *s, PDU &pdu) {
		/* not yet defined */
	}

	void handle_ack(Socket *s, PDU &pdu) {
		/* not yet defined */
	}

	/* override the ReqHandler method. */
	void handle(Socket *s) {
		string str = s->readData();
		PDU pdu(str);
		const char *cstr = pdu.getMethod().c_str();
		switch(phash(cstr)) {
			case METHOD_CONNECT:
				handle_connect(s, pdu);
				break;
			case METHOD_GET:
				handle_get(s, pdu);
				break;
			case METHOD_UPDATE:
				handle_update(s, pdu);
				break;
			case METHOD_ACK:
				handle_ack(s, pdu);
				break;
		}
	}
};

/*
 * args:
 *	host: IP on which to bind the server. This actually initialises
 *  	   the master-config.
 *	port: port on which the server should be listening.
 */
int main(int argc, char *argv[]) {

	if(argc < 3) {
		printf("usage : %s hostaddr port\n", argv[0]);
		return 0;
	}

	Master master(argv[1], argv[2]);
	master.run();
}
