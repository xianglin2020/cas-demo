package store.xianglin;

import java.io.IOException;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException {
        String filePath = "/Users/linxiang/Documents/cas/cas-demo/server.xml";
        Simulation simulation = new Simulation();
        String cookie = simulation.simulationLogin("xianglin", "xianglin");
        System.out.println("cookie => " + cookie);
        simulation.upload(cookie, filePath);
    }
}
