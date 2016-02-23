package edu.kit.informatik.tests.testsuite;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.util.List;

/**
 * InputStream that expects reading and reads specific lines.
 *
 * @author Moritz Hepp
 * @version 1.0
 */
public class ExpectionInputStream extends PipedInputStream {

    private boolean expecting;
    private int count;
    private final List<?> inputs;
    private ExpectionOutputStream out;

    /**
     * Initialises a new InputStream.
     *
     * @param ins
     *            Lines the stream expects.
     */
    public ExpectionInputStream(final List<?> ins) {
        super();
        inputs = ins;
        count = 0;
        expecting = true;
    }

    /**
     * Setter of expecting.
     *
     * @param val
     *            Value of expecting.
     */
    public void setExpecting(final boolean val) {
        expecting = val;
    }

    /**
     * Getter of expecting.
     *
     * @return value of expecting.
     */
    public boolean isExpecting() {
        return expecting;
    }

    @Override
    public void connect(final PipedOutputStream str) throws IOException {
        if (str instanceof ExpectionOutputStream) {
            super.connect(str);
            out = (ExpectionOutputStream) str;
        } else {
            System.err.println(TestSuite.ERR_PREF + "Tried to connect with non ExpectionOutputStream instance!");
            System.exit(-1);
        }
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        int result = -1;
        if (expecting) {
            if (inputs.size() > count) {
                final char[] ch = new char[b.length];
                final StringReader reader = new StringReader(inputs.get(count).toString() + System.lineSeparator());

                result = reader.read(ch, off, len);

                for (int i = off; i < result; i++) {
                    b[i] = (byte) ch[i];
                }
                count++;
                expecting = false;
            } else if (inputs.size() == count) {
                final byte[] by = ("quit" + System.lineSeparator()).getBytes();
                System.arraycopy(by, 0, b, 0, by.length);
                result = by.length;
                expecting = false;
            } else {
                System.err.println(TestSuite.ERR_PREF + "End of expectations reached!");
                System.exit(-2);
            }
        } else {
            if (this.out.isExpecting()) {
                System.err.println(
                        TestSuite.ERR_PREF + "Expecting " + (this.out.getExpectationSize() - this.out.getCount())
                                + " more outputs but got call to read!");
            } else {
                System.err.println(TestSuite.ERR_PREF + "Reading while not expected; case: " + count);
                System.exit(-2);
            }
        }
        return result;
    }
}