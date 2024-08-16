package terraria.worldgen;

public class Maze {

    private int width;
    private int height;
    private boolean[][] cells;

    public Maze(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Maze dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        initializeCells();
    }

    private void initializeCells() {
        cells = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y] = true; // Initialize all to walls
            }
        }
    }

    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean hasWall(int x, int y) {
        // Handle potential out-of-bounds errors
        if (isInBounds(x, y))
            return cells[x][y];
        // Treat out of bounds as walls
        return true;
    }

    public void setWall(int x, int y, boolean hasWall) {
        if (isInBounds(x, y))
            cells[x][y] = hasWall;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}