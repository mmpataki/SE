package fireup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import se.dscore.MasterProxy;
import se.util.Logger;

/**
 *
 * @author Madhusoodan Pataki
 */
public class WrappedProcess {

    String procName;
    String startTime;
    Process process;
    String errorFile, outputFile;

    public static WrappedProcess createProcess(
            String ticket, String newPID,
            String executableName, MasterProxy mproxy,
            String[] arguments) {

        String command[] = null;
        WrappedProcess wp = new WrappedProcess();
        ArrayList<String> cmdchunks = new ArrayList<>();
        File err = new File("./" + newPID + ((new Date()).getTime() + "err.txt"));
        File out = new File("./" + newPID + ((new Date()).getTime() + "out.txt"));

        if (executableName.contains(".jar")) {

            /* check whether the JAVA_HOME is defined */
            String jhome = System.getenv("JAVA_HOME");

            if (jhome == null) {
                Logger.elog(Logger.HIGH, "JAVA_HOME environment variable not set");
            }

            cmdchunks.add(Paths.get(jhome, "bin", "java.exe").toString());
            cmdchunks.add("-jar");
            cmdchunks.add(executableName);
            cmdchunks.add(ticket);
            cmdchunks.add(newPID);
            cmdchunks.add(err.getName());
            cmdchunks.add(out.getName());
            cmdchunks.add(mproxy.getHost());
            cmdchunks.add(mproxy.getPort() + "");
            cmdchunks.addAll(Arrays.asList(arguments));

            command = new String[9 + arguments.length];

        } else {
            cmdchunks.add(executableName);
            cmdchunks.addAll(Arrays.asList(arguments));
        }

        cmdchunks.toArray(command);

        try {
            err.createNewFile();
            out.createNewFile();
        } catch (IOException ex) {
            Logger.elog(Logger.HIGH, "Output/Error file creation failed");
        }

        wp.errorFile = err.getAbsolutePath();
        wp.outputFile = out.getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(err);
        pb.redirectOutput(out);

        wp.procName = command[0];
        wp.startTime = (new Date()).toString();

        try {
            wp.process = pb.start();
            Logger.ilog(Logger.MEDIUM, "Process creation " + Arrays.toString(command) + " was successful");
        } catch (IOException ex) {
            Logger.ilog(Logger.MEDIUM, "Process creation " + Arrays.toString(command)  + " failed");
            return null;
        }
        return wp;
    }

    void kill() {
        process.destroy();
    }

    String getOutputFileName() {
        return outputFile;
    }

    String getErrorFileName() {
        return errorFile;
    }

}
