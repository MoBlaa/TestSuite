package edu.kit.informatik.tests.testsuite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * OutputStream multiple outputs and only writing if output matches the expected
 * output.
 *
 * @author Moritz Hepp
 * @version 1.0
 */
public class ExpectionOutputStream extends PipedOutputStream {

    private final List<?> expectations;
    private int count = 0;
    private boolean newLineAllowed = false;
    private ExpectionInputStream in;
    private final PrintStream out;
    private PrintWriter log;

    /**
     * Initialises a PipedStream with a list of expected inputs.
     *
     * @param expected
     *            Expected lines to print.
     * @param str
     *            Next OutputStream.
     * @param logFile
     *            File to save the output in.
     * @throws IOException
     *             Will be thrown if initialisation of logwriter went wrong.
     */
    public ExpectionOutputStream(final List<?> expected, final OutputStream str, final File logFile)
            throws IOException {
        super();
        expectations = expected;
        this.out = new PrintStream(str);
        if (logFile.exists()) {
            if (!logFile.delete()) {
                throw new IOException("Deleting previous file failed!");
            }
        } else {
            if (!logFile.getParentFile().exists()) {
                if (logFile.mkdirs()) {
                    throw new IOException("Deleting previous file failed!");
                }
            }
        }
        if (logFile.createNewFile()) {
            log = new PrintWriter(new FileWriter(logFile));
        } else {
            throw new IOException("Creating new logFile failed!");
        }
    }

    /**
     * Getter of already printed lines.
     *
     * @return Count of already printed lines.
     */
    public int getCount() {
        return count;
    }

    /**
     * Getter of line count expecting.
     *
     * @return Size of list of expectations.
     */
    public int getExpectationSize() {
        return expectations.size();
    }

    /**
     * Getter of property, if this Stream still expects.
     *
     * @return true, if count of already printed outputs is smaller then the
     *         size of the expected lines, false otherwise.
     */
    public boolean isExpecting() {
        return count < this.expectations.size();
    }

    /**
     * Getter of the next stream this stream is connected to.
     *
     * @return Stream this stream is connected to.
     */
    public PrintStream getNextStream() {
        return this.out;
    }

    @Override
    public void connect(final PipedInputStream in) throws IOException {
        if (in instanceof ExpectionInputStream) {
            super.connect(in);
            this.in = (ExpectionInputStream) in;
        } else {
            System.err.println(TestSuite.ERR_PREF + "Tried to connect with non ExpectionInputStream instance!");
            System.exit(-1);
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        final String out = new String(Arrays.copyOfRange(b, off, len)).replace(System.lineSeparator(), "\n");
        if (out.startsWith(TestSuite.ERR_PREF) || out.startsWith(TestSuite.DEF_PREF) || out.matches("\n")) {
            if (out.matches("\n")) {
                if (this.isExpecting()
                        && this.expectations.get(count).toString().replace(System.lineSeparator(), "\n").equals(out)) {
                    this.log.print(out);
                    this.log.flush();

                    count++;
                    newLineAllowed = true;
                    this.in.setExpecting(true);
                } else if (newLineAllowed || !this.isExpecting()) {
                    this.log.print(out);
                    this.log.flush();
                } else {
                    System.err.println(TestSuite.ERR_PREF + ": expected: " + this.expectations.get(count).toString()
                            + " but got new line!");
                }
            } else {
                this.log.print(out);
                this.log.flush();

                newLineAllowed = true;
            }
        } else {
            if (count < expectations.size()) {
                String sRep = expectations.get(count).toString();

                // Get options
                boolean ignoreEquals = false;
                while (sRep.startsWith("!")) {
                    sRep = sRep.replaceFirst("!", "");
                    // Ignore case
                    if (sRep.startsWith("C")) {
                        sRep = sRep.replaceFirst("C", "");
                        ignoreEquals = sRep.equalsIgnoreCase(out);
                    }
                }
                if ((sRep.equals(out) || ignoreEquals) || (sRep.equals("00err") && out.startsWith("Error"))) {
                    this.log.print(out);
                    this.log.flush();

                    // quit-cmd has to be written
                    in.setExpecting(true);
                    count++;
                    newLineAllowed = true;
                } else {
                    System.err.println(TestSuite.ERR_PREF + "\nexpected: " + sRep + "\nactual: " + out);
                }
            } else {
                System.err.println(TestSuite.ERR_PREF + "End of output reached!");
            }
        }
    }
}