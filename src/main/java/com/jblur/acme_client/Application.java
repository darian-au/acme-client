package com.jblur.acme_client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.IDefaultProvider;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private static final String LOGBACK_CONF = "logback_pattern.xml";
    private static final String APPLICATION_PROPS = "application.properties";

    private static ClassLoader classloader = Thread.currentThread().getContextClassLoader();

    public static void main(final String[] args) {
        Parameters parameters = new Parameters();

        JCommander jCommander = JCommander.newBuilder()
                .addObject(parameters)
                .build();

        IDefaultProvider defaultProvider = configureDefaultProvider(args, parameters.getConfigFilename());

        if (defaultProvider != null) {
            jCommander.setDefaultProvider(defaultProvider);
        }

        try {
            jCommander.parse(args);
        } catch (Exception e) {
            LOG.error("An error occurred while parsing parameters.", e);
            System.out.print(CommandExecutor.RESULT_ERROR);
            return;
        }

        jCommander.setProgramName("java -jar acme-client.jar");

        if (parameters.isHelp()) {
            printHelpInfo(jCommander);
            return;
        }

        if (parameters.isVersion()) {
            printVersion();
            return;
        }

        setupLogDirectory(parameters);

        configureLogger(parameters.getLogDir(),
                (checkLogLevel(parameters.getLogLevel())) ? parameters.getLogLevel() : "WARN",
                LOGBACK_CONF);


        if (!parameters.verifyRequirements()) {
            System.out.print(CommandExecutor.RESULT_ERROR);
            return;
        }

        new CommandExecutor(parameters).execute();
    }

    /**
     * Attempts to locate a configuration file, either specified on the command line, or with a default name.
     * @param args the command line arguments.
     * @param configFilename the default name of the configuration file.
     * @return a default property provider if a configuration file could be found, null otherwise.
     */
    private static IDefaultProvider configureDefaultProvider(final String[] args, final String configFilename) {
        try {
            String[] paramNames = Parameters.class.getDeclaredField("configFilename").getAnnotation(Parameter.class).names();

            if (paramNames != null && paramNames.length > 0) {
                for (int index = 0; index < args.length; index++) {
                    for (String paramName : paramNames) {
                        if (paramName.equals(args[index]) && index + 1 < args.length) {
                            return new PropertyFileDefaultProvider(args[index + 1]);
                        }
                    }
                }
            }

            return new PropertyFileDefaultProvider(configFilename);
        }
        catch (NoSuchFieldException | SecurityException | ParameterException ex) {
            LOG.warn("Unable to locate default configuration. {}", ex.getMessage());
            return null;
        }
    }

    private static void configureLogger(String logDir, final String logLevel, final String logbackConf) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();
            if (!logDir.endsWith(File.separator))
                logDir+= File.separator;
            context.putProperty("LOG_DIR", logDir);
            context.putProperty("LOG_LEVEL", logLevel);

            InputStream is = classloader.getResourceAsStream(logbackConf);
            configurator.doConfigure(is);
        } catch (JoranException je) {
            LOG.warn("Cannot configure logger. Continue to execute the command.", je);
        }
        StatusPrinter.printInCaseOfErrorsOrWarnings(context);
    }

    private static boolean checkLogLevel(final String logLevel) {
        String[] logLevels = new String[]{"OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"};
        boolean valid = false;
        for (String strLogLevel : logLevels) {
            if (strLogLevel.equalsIgnoreCase(logLevel)) {
                valid = true;
                break;
            }
        }
        return valid;
    }

    private static void printHelpInfo(final JCommander jCommander){
        StringBuilder usage = new StringBuilder();
        jCommander.usage(usage);
        System.out.println(usage.toString());
        String format = "%10s%n";
        System.out.format(format, Parameters.MAIN_USAGE.toString());
    }

    private static void printVersion(){
        String implVersion = Application.class.getPackage().getImplementationVersion();

        Properties prop = new Properties();
        try {
            prop.load(classloader.getResourceAsStream(APPLICATION_PROPS));
            String version = prop.getProperty("version");
            System.out.println(String.format(version, implVersion != null ? implVersion : "?"));
        }
        catch (IOException ex) {
            LOG.error("Cannot get version information.", ex);
            System.out.println(CommandExecutor.RESULT_ERROR);
        }
    }

    private static void setupLogDirectory(final Parameters parameters){
        if (!Files.isDirectory(Paths.get(parameters.getLogDir()))) {
            LOG.info("Specified log directory doesn't exist: " + parameters.getLogDir() +
                    "\nTrying to create the log directory.");
            try {
                Files.createDirectories(Paths.get(parameters.getLogDir()));
            } catch (IOException e) {
                LOG.error("Cannot create log directory: " + parameters.getLogDir() + ".\nPlease check filesystem permissions.", e);
                System.out.print(CommandExecutor.RESULT_ERROR);
                return;
            }
            LOG.info("Created log directory: " + parameters.getLogDir());
        }
    }

}