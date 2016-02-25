package edu.kit.informatik.tests.testsuite;

import edu.kit.informatik.Terminal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Testing class providing a text file with command inputs instead of manual
 * input. The text file is formatted as follows:
 * <ul>
 * <li>- '&lt;arg1;arg2&gt;' at the start of the file with arg1 and arg2 as
 * command-line-arguments of testing file.</li>
 * <li>- '#' at the start of a line marks a line-comment.</li>
 * <li>- "&lt;expectedOutput&gt; : \"&lt;input&gt;\""</li>
 * </ul>
 *
 * @author Moritz Hepp
 * @version 1.0
 */
public final class TestSuite {
    /**
     * Prefix for error messages. ExpectionOutputStream will ignore output with
     * this prefix.
     */
    public static final String ERR_PREF = "$Error: ";
    /**
     * Prefix for test messages. ExpectionOutputStream will ignore output with
     * this prefix.
     */
    public static final String DEF_PREF = "Test: ";

    private static final String PARAM_REGEX = "(?:!C)?";

    private static final String LINE_REGEX
            = "(null|00err|true|false|\"" + PARAM_REGEX
            + "[\\w\\s;\\-]*\"|-?[\\d]+|-?[\\d]+\\.[\\d]+)\\s:\\s\"([\\w\\s;]+)\"";
    private static final String CMD_LINE_ARGS_REGEX = "\"[\\w\\\\/:_\\-\\.]+\"(;\"[\\w\\\\/:_\\-\\.]+\")*";

    private static final String CMD_LINE_ARGS = "$CMD_LINE_ARGS$";

    private static ExpectionInputStream sysIn;
    private static ExpectionOutputStream sysOut;

    private static File[] files;
    private static Class<?> cl;
    private static File logDir;

    private static final Queue<Thread> THREAD_QUEUE = new ConcurrentLinkedQueue<>();

    private TestSuite() {
    }

    /**
     * Test main to perform the tests located at $ProjectRoot/tests and named
     * *.test.
     *
     * @param args Command line arguments.
     */
    public static void main(final String... args) {
        init();
        for (final File f : files) {
            final Class<?> clazz = cl;
            final List<String> fileLines = readTestFile(f.getPath());
            if (fileLines != null) {
                Thread thread = new Thread(() -> {
                    final File logFile = new File(
                            logDir.getAbsoluteFile() + "/" + f.getName().replace(".test", "Test.log"));
                    System.out.println(DEF_PREF + "## file: " + f.getName());

                    List<String> inputs = new LinkedList<>();
                    List<String> expectations = new LinkedList<>();

                    convert(fileLines, inputs, expectations);

                    testFile(clazz, inputs, expectations, logFile);
                    if (!THREAD_QUEUE.isEmpty())
                        THREAD_QUEUE.poll().start();
                });
                thread.setDaemon(false);
                THREAD_QUEUE.add(thread);
            } else
                System.err.println(ERR_PREF + "Bad formatted file: " + f.getName());
        }
        if (!THREAD_QUEUE.isEmpty())
            THREAD_QUEUE.poll().start();
        else
            System.err.println(ERR_PREF + "Threading error: Thread queue is empty!");
    }

    private static void init() {
        final Scanner scan = new Scanner(System.in);
        Properties prop = new Properties();
        try {
            prop.load(new FileReader("TestSuite.config"));
        } catch (IOException ignored) {
        }
        File testsDir = null;
        if (prop.containsKey("TestSources"))
            testsDir = new File(prop.getProperty("TestSources"));
        while (testsDir == null || !testsDir.exists()) {
            System.out.print("Path to tests directory: ");
            String input = scan.nextLine();
            testsDir = new File(input);
            if (!testsDir.exists()) {
                System.err.println(ERR_PREF + "Not a valid directory!");
                testsDir = null;
            } else {
                prop.setProperty("TestSources", testsDir.getPath());
            }
        }
        files = testsDir.listFiles((dir, name) -> name.endsWith(".test"));
        if (files == null || files.length == 0) {
            System.err.println(ERR_PREF + "Tests directory doesn't contain .test-Files!");
            System.exit(-1);
        }
        logDir = new File(testsDir.getPath() + "/logs/");
        if (!logDir.exists())
            if (!logDir.mkdir())
                System.err.println(ERR_PREF + "Failed to create log-directory.");
        cl = null;
        String className;
        if (prop.containsKey("TestClass")) {
            try {
                className = prop.getProperty("TestClass");
                cl = Terminal.class.getClassLoader().loadClass("edu.kit.informatik." + className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                cl = null;
            }
        }
        while (cl == null) {
            try {
                System.out.print("Name of testing class: ");
                className = scan.nextLine();
                cl = Terminal.class.getClassLoader().loadClass("edu.kit.informatik." + className);
                prop.setProperty("TestClass", className);
            } catch (ClassNotFoundException e) {
                System.err.println(ERR_PREF + e.getMessage());
                cl = null;
            }
        }
        try {
            prop.store(new FileOutputStream("TestSuite.config"), "TestSuite runtime config");
        } catch (IOException e) {
            System.err.println(ERR_PREF + "Failed storing properties!");
        }
    }

    /**
     * Performs the tests one file is representing.
     *
     * @param testClass    Class to be tested.
     * @param inputs       Inputs with Command line args.
     * @param expectations Expected outputs.
     * @param logFile      File to store the output of this test.
     */
    public static void testFile(final Class<?> testClass, final List<String> inputs,
                                final List<String> expectations, final File logFile) {
        if (inputs != null && expectations != null && !inputs.isEmpty() && !expectations.isEmpty()) {
            try {
                final Method main = testClass.getMethod("main", String[].class);

                String[] arguments = null;
                if (inputs.get(0).startsWith(CMD_LINE_ARGS)) {
                    String cmdLineArgs = inputs.get(0).replace(CMD_LINE_ARGS, "");

                    arguments = cmdLineArgs.split(";");
                    inputs.remove(0);
                }
                initInOutput(inputs, expectations, logFile);

                main.invoke(null, (Object) arguments);

                resetInOutputSettings();
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | IOException e) {
                System.err.println(ERR_PREF + "Something went wrong while testing!");
                e.printStackTrace();
            }
        } else {
            System.err.println(ERR_PREF + "Empty test-file!");
        }
    }

    private static void resetInOutputSettings() {
        final int count = sysOut.getCount();
        if (count < sysOut.getExpectationSize()) {
            System.err
                    .println(ERR_PREF + "Expected output count: " + count + ", actual: " + sysOut.getExpectationSize());
        } else if (sysIn.isExpecting()) {
            System.err.println(ERR_PREF + "Expected input!");
        } else {
            System.setOut(sysOut.getNextStream());
            try {
                sysOut.close();
                sysOut = null;
                sysIn.close();
                sysIn = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static <E> void initInOutput(final List<String> inputs,
                                         final List<String> expectations,
                                         final File logFile) throws IOException {
        TestSuite.sysOut = new ExpectionOutputStream(expectations, System.out, logFile);
        TestSuite.sysIn = new ExpectionInputStream(inputs);

        try {
            sysIn.connect(sysOut);
        } catch (final IOException e) {
            System.err.println(ERR_PREF + e.getMessage());
            System.exit(-1);
            return;
        }

        System.setOut(new PrintStream(TestSuite.sysOut));
        System.setIn(TestSuite.sysIn);
    }

    private static List<String> readTestFile(final String path) {
        List<String> lines = null;
        if (path != null) {
            File testFile;
            if (path.matches("[\\w]+")) {
                testFile = new File(System.getProperty("user.dir") + File.pathSeparatorChar + path);
            } else {
                testFile = new File(path);
            }
            if (testFile.exists()) {
                try {
                    final BufferedReader reader = new BufferedReader(new FileReader(testFile));
                    lines = new LinkedList<>();
                    while (reader.ready()) {
                        String nLine = reader.readLine();
                        if (nLine != null) {
                            // if output is multiple lines long
                            if (nLine.matches("\"[\\w\\s\\-]*") && reader.ready()) {
                                String next;
                                boolean cont = true;
                                while (cont) {
                                    next = reader.readLine();
                                    nLine += "\n" + next;
                                    if (next.matches("[\\w\\s\\-;]*\"\\s:\\s\"[\\w\\s;]+\"")) {
                                        cont = false;
                                    } else if (!reader.ready()) {
                                        nLine = "";
                                        cont = false;
                                    }
                                }
                            }
                            if (nLine.matches(LINE_REGEX)) {
                                lines.add(nLine);
                            } else if (nLine.matches("<" + CMD_LINE_ARGS_REGEX + ">")) {
                                if (lines.size() == 0) {
                                    final String args = nLine.replace("<", "").replace(">", "");
                                    lines.add(args);
                                } else {
                                    lines = null;
                                    break;
                                }
                            } else if (!nLine.matches("#.*") && !nLine.isEmpty()) {
                                lines = null;
                                break;
                            }
                        }
                    }
                } catch (final IOException e) {
                    System.err.println(ERR_PREF + "Something went wrong while reading test File: " + e.getMessage());
                }
            }
        }
        return lines;
    }

    private static void convert(final List<String> lines,
                                final List<String> inputs, final List<String> expections) {
        if (lines != null) {
            //Problem with same command
            for (final String line : lines) {
                if (line != null) {
                    if (line.matches(CMD_LINE_ARGS_REGEX)) {
                        String cmdLineArgs = line.replace("\"", "");
                        inputs.add(CMD_LINE_ARGS + cmdLineArgs);
                    } else {
                        final Pattern pat = Pattern.compile(LINE_REGEX);
                        final Matcher match = pat.matcher(line);
                        if (match.matches() && (match.groupCount() == 2 || match.groupCount() == 3)) {
                            /*
                            group(1) == expected output
                            group() == input
                             */
                            final String expected = match.group(1).replace("\"", "");
                            final String input = match.group(2);

                            expections.add(expected);
                            inputs.add(input);
                        }
                    }
                }
            }
        }
    }
}