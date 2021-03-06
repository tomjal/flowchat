package com.chat.webservice;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.chat.DataSources;
import com.chat.db.Actions;
import com.chat.db.Transformations;
import com.chat.scheduled.ScheduledJobs;
import com.chat.tools.Tools;
import com.chat.types.*;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.sdk.Context;
import org.javalite.activejdbc.LazyList;
import org.javalite.activejdbc.Paginator;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static com.chat.db.Tables.*;
import static spark.Spark.*;

public class ChatService {

    static Logger log = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @Option(name="-loglevel", usage="Sets the log level [INFO, DEBUG, etc.]")
    private String loglevel = "INFO";

    @Option(name="-ssl",usage="The location of the java keystore .jks file.")
    private File jks;

    @Option(name="-docker",usage="Use the docker container")
    private Boolean docker = false;

    @Option(name="-liquibase", usage="Run liquibase changeset")
    private Boolean liquibase = true;

    @Option(name="-reddit_import", usage="Fetch posts from reddit")
    private Boolean redditImport = false;

    public void doMain(String[] args) {

        parseArguments(args);

        log.setLevel(Level.toLevel(loglevel));
        log.getLoggerContext().getLogger("org.eclipse.jetty").setLevel(Level.OFF);
        log.getLoggerContext().getLogger("spark.webserver").setLevel(Level.OFF);

        if (jks != null) {
            Spark.secure(jks.getAbsolutePath(), "changeit", null,null);
            DataSources.SSL = true;
        }

        if (docker) {
            DataSources.PROPERTIES.setProperty("jdbc.url", "jdbc:postgresql://db/flowchat");
            DataSources.PROPERTIES.setProperty("jdbc.password", "test");
        }

        if (liquibase) {
            Tools.runLiquibase();
        }

        staticFiles.location("/dist");
        staticFiles.expireTime(600);

        // Instantiates the ranking constants
        ConstantsService.INSTANCE.getRankingConstants();

        // Set up websocket
        webSocket("/threaded_chat", ThreadedChatWebSocket.class);

        // Set up endpoints
        Endpoints.status();
        Endpoints.user();
        Endpoints.discussion();
        Endpoints.community();
        Endpoints.tag();
        Endpoints.reply();
        Endpoints.exceptions();

        init();

        if (redditImport) {
            ScheduledJobs.start();
        }

    }

    private void parseArguments(String[] args) {
        CmdLineParser parser = new CmdLineParser(this);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            // if there's a problem in the command line,
            // you'll get this exception. this will report
            // an error message.
            System.err.println(e.getMessage());
            System.err.println("java -jar reddit-history.jar [options...] arguments...");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();
            System.exit(0);

            return;
        }
    }

    public static void main(String[] args) throws Exception {
        new ChatService().doMain(args);
    }

}
