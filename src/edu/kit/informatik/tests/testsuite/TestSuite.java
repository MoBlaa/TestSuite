package edu.kit.informatik.tests.testsuite;

import edu.kit.informatik.Terminal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
            + "[\\w\\s]*\"|-?[\\d]+|-?[\\d]+\\.[\\d]+)\\s:\\s\"([\\w\\s;]+)\"";
    private static final String CMD_LINE_ARGS_REGEX = "\"[\\w\\\\/:_\\-\\.]+\"(;\"[\\w\\\\/:_\\-\\.]+\")*";

    private static final String CMD_LINE_ARGS = "$CMD_LINE_ARGS$";

    private static ExpectionInputStream sysIn;
    private static ExpectionOutputStream sysOut;

    private static final ConcurrentLinkedQueue<Thread> THREAD_QUEUE = new ConcurrentLinkedQueue<>();

    private TestSuite() {
    }

    /**
     * Test main to perform the tests located at $ProjectRoot/tests and named
     * *.test.
     *
     * @param args Command line arguments.
     */
    public static void main(final String... args) {
        // Init
        final Scanner scan = new Scanner(System.in);

        Properties prop = new Properties();
        try {
            prop.load(TestSuite.class.getResourceAsStream("TestSuite.config"));
        } catch (IOException e) {
            System.err.println(ERR_PREF + "Failed to read config file!");
        }
        if (!prop.isEmpty()) {
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
                }
            }
            final File[] files = testsDir.listFiles((dir, name) -> name.endsWith(".test"));
            if (files == null || files.length == 0) {
                System.err.println(ERR_PREF + "Tests directory doesn't contain .test-Files!");
                System.exit(-1);
            }

            final File logDir = new File(testsDir.getPath() + "/logs/");
            if (!logDir.exists())
                if (!logDir.mkdir())
                    System.err.println(ERR_PREF + "Failed to create log-directory.");

            Class<?> cl = null;
            String className = null;
            if (prop.containsKey("TestClass"))
                className = prop.getProperty("TestClass");
            while (className == null || cl == null) {
                try {
                    cl = Terminal.class.getClassLoader().loadClass("edu.kit.informatik." + className);
                    continue;
                } catch (ClassNotFoundException e) {
                    System.err.println(ERR_PREF + e.getMessage());
                    cl = null;
                }
                System.out.print("Name of testing class: ");
                className = scan.nextLine();
            }

            for (final File f : files) {
                final String[] fileLines = readTestFile(f.getPath());
                if (fileLines != null) {
                    final File logFile = new File(
                            logDir.getAbsoluteFile() + "/" + f.getName().replace(".test", "Test.log"));

                    final Class<?> inst = cl;
                    Thread prev = new Thread(() -> {
                        System.out.println(DEF_PREF + "## file: " + f.getName());

                        testFile(inst, convert(fileLines), logFile);
                        if (!THREAD_QUEUE.isEmpty())
                            THREAD_QUEUE.poll().start();
                    });
                    prev.setDaemon(false);
                    THREAD_QUEUE.add(prev);
                } else
                    System.err.println(ERR_PREF + "Bad formatted file: " + f.getName());
            }
            if (!THREAD_QUEUE.isEmpty())
                THREAD_QUEUE.poll().start();
            else
                System.exit(-2);
        } else
            System.err.println(ERR_PREF + "No configs were found!");
    }

    /**
     * Performs the tests one file is representing.
     *
     * @param testClass Class to be tested.
     * @param cas       Assertions with in and output expected.
     * @param logFile   File to store the output of this test.
     */
    public static void testFile(final Class<?> testClass, final Map<String, ?> cas, final File logFile) {
        if (!cas.isEmpty()) {
            try {
                final Method main = testClass.getMethod("main", String[].class);

                Object arguments = null;
                if (cas.containsKey(CMD_LINE_ARGS)) {
                    arguments = cas.get(CMD_LINE_ARGS);
                    cas.remove(CMD_LINE_ARGS);
                }
                initInOutput(cas, logFile);

                main.invoke(null, arguments);

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

    private static <E> void initInOutput(final Map<String, E> map, final File logFile) throws IOException {
        final List<E> expected = new LinkedList<>();
        final List<String> inputs = new LinkedList<>();

        map.keySet().stream().filter(input -> input != null).forEach(input -> {
            inputs.add(input);
            if (map.get(input) != null) {
                expected.add(map.get(input));
            }
        });

        TestSuite.sysOut = new ExpectionOutputStream(expected, System.out, logFile);
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

    private static String[] readTestFile(final String path) {
        String[] lines = null;
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
                    List<String> list = new LinkedList<>();
                    while (reader.ready()) {
                        String nLine = reader.readLine();
                        if (nLine != null) {
                            // if output is multiple lines long
                            if (nLine.matches("\"[\\w\\s]*") && reader.ready()) {
                                String next;
                                boolean cont = true;
                                while (cont) {
                                    next = reader.readLine();
                                    nLine += "\n" + next;
                                    if (next.matches("[\\w\\s]*\"\\s:\\s\"[\\w\\s;]+\"")) {
                                        cont = false;
                                    } else if (!reader.ready()) {
                                        nLine = "";
                                        cont = false;
                                    }
                                }
                            }
                            if (nLine.matches(LINE_REGEX)) {
                                list.add(nLine);
                            } else if (nLine.matches("<" + CMD_LINE_ARGS_REGEX + ">")) {
                                if (list.size() == 0) {
                                    final String args = nLine.replace("<", "").replace(">", "");
                                    list.add(args);
                                } else {
                                    list = null;
                                    break;
                                }
                            } else if (!nLine.matches("#.*") && !nLine.isEmpty()) {
                                list = null;
                                break;
                            }
                        }
                    }
                    if (list != null) {
                        lines = list.toArray(new String[list.size()]);
                    }
                } catch (final IOException e) {
                    System.err.println(ERR_PREF + "Something went wrong while reading test File: " + e.getMessage());
                }
            }
        }
        return lines;
    }

    private static Map<String, ?> convert(final String[] lines) {
        Map<String, Object> cases = null;
        if (lines != null) {
            cases = new LinkedHashMap<>();
            for (final String line : lines) {
                if (line != null) {
                    if (line.matches(CMD_LINE_ARGS_REGEX)) {
                        String cmdLineArgs = line.replace("\"", "");
                        String[] splited = cmdLineArgs.split(";");
                        cases.put(CMD_LINE_ARGS, splited);
                    } else {
                        final Pattern pat = Pattern.compile(LINE_REGEX);
                        final Matcher match = pat.matcher(line);
                        if (match.matches() && (match.groupCount() == 2 || match.groupCount() == 3)) {
                            /*
                            group(1) == expected output
                            group(2) == input
                             */
                            final String expected = match.group(1);
                            final String input = match.group(2);

                            cases.put(input, expected.replace("\"", ""));
                        }
                    }
                }
            }
            if (cases.size() == 0) {
                cases = null;
            }
        }
        return cases;
    }
}