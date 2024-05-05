package terraria.worldgen.overworld;

public class MazeGenerator {
    public static final int RESERVED = -1, WALL = 1, CORRIDOR = 0;
    public int[][] grid;

    public MazeGenerator(int row, int col) {
        grid = new int[row][col];
        for (int i = 0; i < row; i ++)
            for (int j = 0; j < col; j ++)
                grid[i][j] = WALL;
    }
    public void generate(int startX, int startZ, int endX, int endZ) {
        
    }
}
