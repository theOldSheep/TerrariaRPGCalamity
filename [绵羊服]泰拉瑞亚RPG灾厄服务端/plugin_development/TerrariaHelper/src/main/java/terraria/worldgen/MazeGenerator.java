package terraria.worldgen;

import java.util.*;

public abstract class MazeGenerator {
    protected static final int[] DIRECTIONS = {0, 2, 0, -2, -2, 0, 2, 0}; // Right, down, left, up
    protected static Random random = new Random(); // Single random instance for consistency


    // A small class to store a cell and its edge weight
    protected static class CellEdge {
        public int[] from;
        public int[] cell;
        public double weight;

        public CellEdge(int[] from, int[] cell, double weight) {
            this.from = from;
            this.cell = cell;
            this.weight = weight;
        }
    }

    protected ArrayList<int[]> getNeighbors(Maze maze, int x, int y, boolean unvisitedOnly) {
        ArrayList<int[]> neighbors = new ArrayList<>();
        for (int i = 0; i < DIRECTIONS.length; i += 2) {
            int newX = x + DIRECTIONS[i];
            int newY = y + DIRECTIONS[i + 1];

            if (maze.isInBounds(newX, newY) &&
                    (!unvisitedOnly || maze.hasWall( newX, newY) )) { // Check if wall is needed
                neighbors.add(new int[]{newX, newY});
            }
        }
        Collections.shuffle(neighbors, random); // Shuffle using the shared Random object
        return neighbors;
    }

    protected void createPath(Maze maze, int[] from, int[] to) {
        int midX = (from[0] + to[0]) / 2;
        int midY = (from[1] + to[1]) / 2;
        maze.setWall(midX, midY, false);
        maze.setWall(to[0], to[1], false);
    }

    public abstract void generate(Maze maze);
}
