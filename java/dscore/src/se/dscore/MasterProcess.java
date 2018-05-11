package se.dscore;

/**
 * This is a abstract implementation of the Master which can be extended to the
 * implement the required functionality. It manages the status of the domain, a
 * http-server for serving the insights. It also uses a scheduler plug-in to
 * schedule the processes on the slaves.
 *
 * The functionalities already implemented by this class are
 *
 * 1. Handling the connect requests, registering the slave machines. 2.
 * Scheduling the processes on the nodes. 3. Handling heartbeat signals sent by
 * the nodes/ 4. Managing the HTTP-server for providing the insights.
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import se.ipc.ESocket;
import se.ipc.pdu.AckPDU;
import se.ipc.pdu.ConnectPDU;
import se.ipc.pdu.IntroPDU;
import se.ipc.pdu.PDU;
import se.ipc.pdu.PDUConsts;
import se.ipc.pdu.StatusPDU;
import se.util.Logger;
import se.util.http.HttpServer;
import se.util.http.ProgressiveProcess;
import se.util.http.RESTServer;

public class MasterProcess extends Process implements ProgressiveProcess {

    protected final MasterView status;
    protected final LinkedHashMap<String, NodeProxy> nodes;
    private final HttpServer hserver;
    protected Scheduler scheduler;
    private final long SLAVE_HB_WAIT_INTERVAL = 12000;

    /**
     * @param config
     * @throws IOException
     * @throws java.io.FileNotFoundException
     */
    public MasterProcess(MasterProcessConfiguration config)
            throws IOException, FileNotFoundException {

        super(config);
        nodes = new LinkedHashMap<>();
        status = new MasterView(nodes);

        hserver = new RESTServer((String) config.get(MasterProcessConfiguration.HTTP_ROOT), this);

        AckPDU.setHttpPort(hserver.getPort());
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /* Planning to remove this */
    void introduce(IntroPDU pdu) throws IOException {

        for (String slaveHost : nodes.keySet()) {
            NodeProxy slave = nodes.get(slaveHost);
            HashMap<String, ProcessProxy> map = slave.getProcesses();
            for (String key : map.keySet()) {
                map.get(key).sendPDU(pdu, false);
            }
        }
    }

    /**/
    /**
     * Registers the Node, Process in the domain.
     *
     * 1. Fireup is treated as a Node agent and will be added as slave 2.
     * Processes on nodes get added to respective SlaveProxies. 3. Any guests
     * will be introduced to the processes.
     *
     * @param sock
     * @param pdu
     * @throws IOException
     */
    public void handle_connect(ESocket sock, ConnectPDU pdu) throws IOException {

        String sender = sock.getHost();
        String ticket = pdu.getTicket();

        if (pdu.getWho().equals(PDUConsts.PN_FIREUP)) {

            /* check whether a ticket is already assigned */
            if (nodes.get(ticket) != null) {
                return;
            }

            /* We will generate a ticket for the fireup to identify it */
            ticket = "Node-" + nodes.size();
            nodes.put(ticket,
                    new NodeProxy(
                            this, sender,
                            pdu.getConnectPort(),
                            pdu.getHttpPort(),
                            pdu.getSysInfo()
                    )
            );

            AckPDU apdu = new AckPDU(ticket);
            sock.send(apdu);
            schedule(ticket);
            return;
        }

        String pid = pdu.getPid();
        nodes.get(ticket).addProcessEntry(
                pid,
                new ProcessProxy(
                        sender,
                        pdu.getConnectPort(),
                        pdu.getWho(),
                        pid,
                        pdu.getErrFile(),
                        pdu.getOutFile(),
                        pdu.getHttpPort()
                )
        );

        AckPDU apdu = new AckPDU(ticket);
        sock.send(apdu);
        introduce(new IntroPDU(sender, pdu.getConnectPort(), pdu.getWho()));
    }

    int getSlaveCount() {
        return nodes.size();
    }

    void schedule(String host) throws IOException {
        scheduler.schedule(host, nodes);
    }

    void handle_update(ESocket s, PDU pdu) {
        /* not yet defined */
    }

    void handle_ack(ESocket s, PDU pdu) {
        /* not yet defined */
    }

    /* just leaving for backward compatibility */
    void handle_get(ESocket s, PDU pdu) {
    }

    /**
     * Handles the connect and status PDUs
     *
     * @param socket Socket on which PDUs are been sent.
     * @param pdu
     * @throws IOException
     */
    @Override
    public void handler(ESocket socket, PDU pdu) throws IOException {
        switch (pdu.getMethod()) {
            case PDUConsts.METHOD_CONNECT:
                handle_connect(socket, (ConnectPDU) pdu);
                break;
            case PDUConsts.METHOD_STATUS:
                handle_status(socket, (StatusPDU) pdu);
                break;
        }
        super.handler(socket, pdu);
    }


    /**
     * Creates a HTTP-server and starts node protocol handler
     */
    @Override
    public void run() {

        /* Start a HTTP thread */
        new Thread(() -> {
            try {
                hserver.run();
            } catch (IOException ex) {
                Logger.elog(Logger.MEDIUM, "HttpServer encountered an errror");
            }
        }, "HTTP-SERVER").start();

        /* Start a slave monitor thread */
        new Thread(() -> {
            runSlaveMonitorThread();
        }, "SLAVE_MONITOR").start();

        super.run();
    }

    private void runSlaveMonitorThread() {
        while (true) {
            removeDeadSlaves();
            try {
                Thread.sleep(HEARTBEAT_INTERVAL);
            } catch (InterruptedException ex) {
            }
        }
    }

    private void removeDeadSlaves() {
        synchronized (nodes) {
            for (String node : nodes.keySet()) {
                NodeProxy np = nodes.get(node);
                for (String pid : np.processes.keySet()) {
                    ProcessProxy proc = np.processes.get(pid);
                    long timeNow = (new Date()).getTime();
                    if (proc.getLastHBTime() + SLAVE_HB_WAIT_INTERVAL < timeNow) {
                        np.processes.remove(pid);
                        Logger.ilog(Logger.MEDIUM, "Killed the process [" + pid + "] on node [" + node + "]");
                    }
                }
            }
        }
    }

    public HttpServer getHttpServer() {
        return hserver;
    }

    /**
     * Heartbeats are handled here
     *
     * @param socket
     * @param pdu
     */
    private void handle_status(ESocket socket, StatusPDU pdu) {
        try {
            NodeProxy node = nodes.get(pdu.getTicket());
            node.rcvHeartBeat(pdu);
            node.processes.get(pdu.getPid()).rcvHeartbeat(pdu);
        } catch (Exception ex) {
        }
    }

    @Override
    public Object getProgress() {
        return status;
    }

}