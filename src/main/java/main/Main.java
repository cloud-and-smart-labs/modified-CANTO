package main;

public class Main {
    public static void main(String[] args) {
        System.out.println("In main");
        
        String role = args[0];
        String port = args[1];
        
        switch (role) {
        case "master":
            MainMaster.main(new String[]{port});
            break;
        case "worker":
            MainWorker.main(new String[]{port});
            break;
        default:
            System.out.println("ERROR PARSING INPUT");
        }
    }
}