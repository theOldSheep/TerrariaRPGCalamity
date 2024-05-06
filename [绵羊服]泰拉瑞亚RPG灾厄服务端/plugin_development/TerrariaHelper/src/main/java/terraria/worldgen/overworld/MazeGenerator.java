package terraria.worldgen.overworld;

import java.util.*;

public class MazeGenerator {
    private int width;
    private int height;
    private boolean[][] cells;  // true represents a wall, false represents open space

    public MazeGenerator(int width, int height) {
        this.width = width;
        this.height = height;
        cells = new boolean[width][height];

        // Initialize all cells to walls
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y] = true;
            }
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean hasWall(int x, int y) {
        // Handle potential out-of-bounds errors
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return true; // Treat out of bounds as walls
        }
        return cells[x][y];
    }

    public void setWall(int x, int y, boolean hasWall) {
        // Handle potential out-of-bounds errors
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        cells[x][y] = hasWall;
    }

    public void display() {
        // Top border
        System.out.print(" "); // Corner space
        for (int x = 0; x < width; x++) {
            System.out.print("__"); // Top wall segments
        }
        System.out.println();

        // Rows
        for (int y = 0; y < height; y++) {
            System.out.print("|"); // Left border
            for (int x = 0; x < width; x++) {
                System.out.print(cells[x][y] ? "██" : "  ");
            }
            System.out.println("|");  // Right border
        }
    }





    private boolean isValidNeighbor(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height && cells[x][y];
    }




    // A small class to store a cell and its edge weight
    private static class CellEdge {
        public int[] from;
        public int[] cell;
        public double weight;

        public CellEdge(int[] from, int[] cell, double weight) {
            this.from = from;
            this.cell = cell;
            this.weight = weight;
        }
    }


    public void generateMazePrims() {
        PriorityQueue<CellEdge> cellList = new PriorityQueue<>(Comparator.comparingDouble(a -> a.weight));

        int startX = (int) (Math.random() * width);
        int startY = (int) (Math.random() * height);
        // make sure the start coordinates are even
        startX -= startX % 2;
        startY -= startY % 2;
        cells[startX][startY] = false;

        addNeighborsToCellList(cellList, startX, startY); // Add the initial neighbors

        while (!cellList.isEmpty()) {
            // Randomly pick an edge from the list
            CellEdge cellEdge = cellList.poll();
            System.out.println( cellList.peek().weight + ", " + cellEdge.weight);

            int[] from = cellEdge.from, cell = cellEdge.cell;
            if (! cells[cell[0]][cell[1]])
                continue;

            cells[(from[0] + cell[0]) / 2][(from[1] + cell[1]) / 2] = false;
            cells[cell[0]][cell[1]] = false;

            // Add new neighbors to the list
            addNeighborsToCellList(cellList, cell[0], cell[1]);
        }
    }

    private void addNeighborsToCellList(PriorityQueue<CellEdge> cellList, int x, int y) {
        int[] directions = {0, 2, 0, -2, -2, 0, 2, 0};
        for (int i = 0; i < directions.length; i += 2) {
            int newX = x + directions[i];
            int newY = y + directions[i + 1];

            if (isValidNeighbor(newX, newY)) {
                cellList.add(new CellEdge(new int[]{x, y}, new int[]{newX, newY}, Math.random() * 1000)); // Assign random weights
            }
        }
    }


}


