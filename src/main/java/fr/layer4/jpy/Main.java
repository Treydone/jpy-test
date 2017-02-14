package fr.layer4.jpy;

import org.jpy.PyInputMode;
import org.jpy.PyLib;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Created by devil on 13/02/17.
 */
public class Main {

    public static void main(String... args) throws Exception {

        PyLib.Diag.setFlags(PyLib.Diag.F_OFF);

        // Prepare required system properties like 'jpy.jpyLib' and others
        Properties properties = new Properties();
        properties.load(new FileInputStream(ResourceUtils.getFile("classpath:jpyconfig.properties")));
        properties.forEach((k, v) -> System.setProperty((String) k, (String) v));

        if (!PyLib.isPythonRunning()) {

            List<String> extraPaths = Arrays.asList(
                    "classpath:deps-bundle.zip", // REQUIRED: contains all the dependencies
                    "classpath:my_plugin.py" // OPTIONAL: my custom scripts
            );
            List<String> cleanedExtraPaths = new ArrayList<>(extraPaths.size());

            Path tempDirectory = Files.createTempDirectory("lib-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileSystemUtils.deleteRecursively(tempDirectory.toFile())));
            cleanedExtraPaths.add(tempDirectory.toString());

            extraPaths.forEach(lib -> {
                if (lib.startsWith("classpath:")) {
                    try {
                        String finalLib = lib.replace("classpath:", "");
                        Path target = Paths.get(tempDirectory.toString(), finalLib);
                        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream(finalLib)) {
                            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                        if (finalLib.endsWith(".zip")) {
                            ZipUtils.extract(target.toFile(), tempDirectory.toFile());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    cleanedExtraPaths.add(lib);
                }
            });

            PyLib.startPython(cleanedExtraPaths.toArray(new String[]{}));
        }

        try {
            // Exec a python script
            PyObject.executeCode("print 'this is from python'", PyInputMode.SCRIPT);

            // Proxify the call to a python class
            PyModule plugInModule = PyModule.importModule("my_plugin");
            PyObject plugInObj = plugInModule.call("MyPlugin");
            MyPlugin plugIn = plugInObj.createProxy(MyPlugin.class);

            String[] results = plugIn.process("Abcdefghi jkl mnopqr stuv wxy z");
            System.err.println(StringUtils.arrayToCommaDelimitedString(results));

            PyObject response = plugIn.curl("https://api.github.com/users/treydone");
            System.err.println(response);
            System.err.println(response.getAttribute("status_code").getIntValue()); // r.status_code
            System.err.println(response.getAttribute("text").getStringValue()); // r.text
            System.err.println(response.call("json").call("get", "login").getStringValue()); // r.json['login']
            System.err.println(response.getAttribute("headers").call("get", "content-type").getStringValue()); // r.headers['content-type']
        } finally {
            PyLib.Diag.setFlags(PyLib.Diag.F_OFF);
            PyLib.stopPython();
        }
    }

    public interface MyPlugin {
        String[] process(String arg);

        PyObject curl(String url);
    }
}
