package com.example.easteregghuntbe; // Note: If you moved this inside a 'controller' folder earlier, change this to: package com.example.easteregghuntbe.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "https://easter-fe.tom4s-fr4ncisco.workers.dev") // Allow React to connect
public class GameController {

    // Simple in-memory storage for demonstration
    private final List<Spot> spots = Arrays.asList(
            new Spot(0, "START", "ENIGMA", "Bem-vindos! O primeiro ovo está na base do Grande Carvalho.", "CARVALHO-OVO"),
            new Spot(1, "A", "ANAGRAM", "Desembaralha: O Ç I O L A B", "BALOICO-OVO"),
            // A pista 2 (Sudoku) agora envia a grelha, a solução e a pista escondida!
            new Spot(2, "B", "SUDOKU", "{\"initial\":[[0,3,4,0],[4,0,0,2],[1,0,0,3],[0,2,1,0]],\"solution\":[[2,3,4,1],[4,1,3,2],[1,4,2,3],[3,2,1,4]],\"clue\":\"Boa! A tua próxima pista: Procura onde se faz o 'número dois'.\"}", "WC-OVO"),
            new Spot(3, "C", "ENIGMA", "Tenho escorrega mas não sou um parque. (A piscina)", "PISCINA-OVO")
    );

    // Track teams in memory: Maps the Team Color to their progress and time stats
    private final Map<String, TeamState> teams = new HashMap<>();

    // Define starting offsets (Step 1 onwards)
    private final Map<String, Integer> teamOffsets = Map.of(
            "RED", 1,
            "BLUE", 2,
            "GREEN", 3
    );

    @PostMapping("/start")
    public ResponseEntity<?> startTeam(@RequestParam String color) {
        String c = color.toUpperCase();
        // Start the timer for the team if they haven't started yet
        teams.putIfAbsent(c, new TeamState(0, System.currentTimeMillis(), 0));
        return ResponseEntity.ok(Map.of("message", "Team initialized", "step", teams.get(c).step()));
    }

    @GetMapping("/clue")
    public ResponseEntity<?> getClue(@RequestParam String color) {
        String c = color.toUpperCase();
        int step = teams.containsKey(c) ? teams.get(c).step() : 0;

        if (step == 0) {
            // Step 0 is the exact same for everyone
            return ResponseEntity.ok(spots.get(0));
        }

        if (step >= spots.size()) {
            return ResponseEntity.ok(Map.of("finished", true));
        }

        // The Carousel Logic
        int offset = teamOffsets.getOrDefault(c, 1);
        int totalRotatingSpots = spots.size() - 1; // Subtract 1 because Spot 0 is universal

        // Calculate the current spot index based on step and team offset
        int spotIndex = 1 + ((step - 1 + offset) % totalRotatingSpots);

        return ResponseEntity.ok(spots.get(spotIndex));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCode(@RequestParam String color, @RequestParam String code) {
        String c = color.toUpperCase();
        TeamState state = teams.getOrDefault(c, new TeamState(0, System.currentTimeMillis(), 0));
        int step = state.step();

        Spot currentSpot = getSpotForTeamAtStep(c, step);

        if (currentSpot != null && currentSpot.unlockCode().equalsIgnoreCase(code.trim())) {
            int nextStep = step + 1;
            // If they reached the end, record the End Time. Otherwise, keep it 0.
            long endTime = (nextStep >= spots.size()) ? System.currentTimeMillis() : 0;

            teams.put(c, new TeamState(nextStep, state.startTime(), endTime));

            return ResponseEntity.ok(Map.of("success", true, "nextStep", nextStep));
        }

        return ResponseEntity.ok(Map.of("success", false, "message", "Incorrect code!"));
    }

    // Helper method to duplicate the logic for verification
    private Spot getSpotForTeamAtStep(String color, int step) {
        if (step == 0) return spots.get(0);
        if (step >= spots.size()) return null;

        int offset = teamOffsets.getOrDefault(color.toUpperCase(), 1);
        int spotIndex = 1 + ((step - 1 + offset) % (spots.size() - 1));
        return spots.get(spotIndex);
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetTeam(@RequestParam String color) {
        String c = color.toUpperCase();
        // Reset this specific team back to step 0 and restart their timer
        teams.put(c, new TeamState(0, System.currentTimeMillis(), 0));
        return ResponseEntity.ok(Map.of("message", "Team " + color + " reset to start!"));
    }

    @PostMapping("/reset-all")
    public ResponseEntity<?> resetAllTeams() {
        // This clears the entire Map: everyone is forgotten and timers are killed
        teams.clear();
        return ResponseEntity.ok(Map.of("message", "Global Reset Complete! All teams cleared."));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        List<Map<String, Object>> statsList = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<String, TeamState> entry : teams.entrySet()) {
            TeamState ts = entry.getValue();
            // Calculate how long they've been playing (or total time if finished)
            long duration = ts.endTime() > 0 ? (ts.endTime() - ts.startTime()) : (now - ts.startTime());
            boolean finished = ts.endTime() > 0;

            statsList.add(Map.of(
                    "color", entry.getKey(),
                    "durationMs", duration,
                    "finished", finished,
                    "step", ts.step()
            ));
        }

        // Sort the leaderboard: Finished teams first, then by fastest time
        statsList.sort((a, b) -> {
            boolean aFin = (boolean) a.get("finished");
            boolean bFin = (boolean) b.get("finished");
            if (aFin && !bFin) return -1;
            if (!aFin && bFin) return 1;
            return Long.compare((long) a.get("durationMs"), (long) b.get("durationMs"));
        });

        return ResponseEntity.ok(statsList);
    }
}

// Simple record for the Spot data structure
record Spot(int id, String name, String puzzleType, String content, String unlockCode) {}

// Keeps track of what step they are on, and their times
record TeamState(int step, long startTime, long endTime) {}