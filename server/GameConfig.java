package server;

import java.io.*;
import java.util.*;

/**
 * Loads data/config.txt and exposes values as plain fields.
 *
 * File format:
 *   key=value
 *   # comment lines are ignored
 */
public class GameConfig {

    public int          questionDurationSeconds = 15;
    public List<Integer> timerWarnings          = Arrays.asList(10, 5, 3);
    public int          minPlayersPerTeam       = 1;
    public int          maxPlayersPerTeam       = 4;
    public int          maxConcurrentRooms      = 10;
    public int          defaultQuestionCount    = 5;
    public int          scorePerCorrectAnswer   = 10;
    public int          bonusScore              = 5;
    public int          bonusThresholdSeconds   = 5;

    public void load(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] kv = line.split("=", 2);
                if (kv.length != 2) continue;
                String key = kv[0].trim();
                String val = kv[1].trim();
                switch (key) {
                    case "questionDurationSeconds": questionDurationSeconds = Integer.parseInt(val); break;
                    case "timerWarnings":
                        timerWarnings = new ArrayList<>();
                        for (String s : val.split(",")) timerWarnings.add(Integer.parseInt(s.trim()));
                        break;
                    case "minPlayersPerTeam":     minPlayersPerTeam     = Integer.parseInt(val); break;
                    case "maxPlayersPerTeam":     maxPlayersPerTeam     = Integer.parseInt(val); break;
                    case "maxConcurrentRooms":    maxConcurrentRooms    = Integer.parseInt(val); break;
                    case "defaultQuestionCount":  defaultQuestionCount  = Integer.parseInt(val); break;
                    case "scorePerCorrectAnswer": scorePerCorrectAnswer = Integer.parseInt(val); break;
                    case "bonusScore":            bonusScore            = Integer.parseInt(val); break;
                    case "bonusThresholdSeconds": bonusThresholdSeconds = Integer.parseInt(val); break;
                    default:
                        System.err.println("[GameConfig] Unknown key: " + key);
                }
            }
        }
        System.out.println("[GameConfig] Loaded. Question duration: " + questionDurationSeconds + "s");
    }
}
