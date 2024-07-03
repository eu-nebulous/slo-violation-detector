package utility_beans.generic_component_functionality;

public class OutputFormattingPhase {
    public static String firstline = "";
    public static StringBuilder headline = new StringBuilder();
    public static StringBuilder padding = new StringBuilder();
    public static int number_of_dashes = 80;
    public static String phase_start( String title, int lines){


        firstline = padding.append("\n".repeat(Math.max(1,lines/3)))+title+"\n";

        headline = new StringBuilder();

        headline.append("-".repeat(number_of_dashes)).append("\n");
        padding = new StringBuilder();
        padding.append("\n".repeat(Math.max(0, lines)));
        return headline+firstline+headline+padding;
    }

    public static String phase_end(int lines){
        headline = new StringBuilder();
        headline.append("-".repeat(number_of_dashes)).append("\n");
        padding = new StringBuilder();
        padding.append("\n".repeat(Math.max(0, lines)));
        return headline.append(padding).toString();
    }
}
