package ru.makkarpov.mtoxy;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import joptsimple.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;

public class MToxy {
    private static final Logger LOG = LoggerFactory.getLogger(MToxy.class);

    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();

        ArgumentAcceptingOptionSpec<String> configSpec = parser
                .acceptsAll(asList("c", "config"), "Config file location")
                .withRequiredArg()
                .defaultsTo("config.conf");

        AbstractOptionSpec<Void> helpSpec = parser
                .acceptsAll(asList("h", "help", "?"), "Show this help")
                .forHelp();

        OptionSet opts;

        try {
            opts = parser.parse(args);
        } catch (OptionException e) {
            LOG.error("Failed to parse command line options!", e);
            System.exit(2);
            return;
        }

        if (opts.has(helpSpec)) {
            parser.printHelpOn(System.err);
            return;
        }

        File configFile = new File(opts.valueOf(configSpec));
        if (!configFile.exists() || !configFile.isFile()) {
            LOG.error("Config file [{}] does not exists or is not a file!", configFile.getCanonicalPath());
            System.exit(2);
            return;
        }

        Map<String, Object> generatedConfig = new HashMap<>();

        generatedConfig.put("directory", configFile.getCanonicalFile().getParentFile().getCanonicalPath());

        Config cfg = ConfigFactory.parseFile(configFile)
                .withFallback(ConfigFactory.parseResources(MToxy.class, "/reference.conf"))
                .withFallback(ConfigFactory.parseMap(generatedConfig))
                .resolve();

        Injector injector = Guice.createInjector((Module) binder -> {
            binder.bind(Config.class).toInstance(cfg);
        });

        injector.getInstance(MToxy.class).run();
    }

    private MTServer mtServer;

    @Inject
    public MToxy(MTServer mtServer) {
        this.mtServer = mtServer;
    }

    private void run() {
        LOG.info("Starting mtoxy...");
        mtServer.start();
    }
}
