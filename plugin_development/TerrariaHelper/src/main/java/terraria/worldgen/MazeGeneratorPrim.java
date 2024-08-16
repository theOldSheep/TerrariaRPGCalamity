package terraria.worldgen;

import java.util.Comparator;
import java.util.PriorityQueue;

public class MazeGeneratorPrim extends MazeGenerator {
    protected void addNeighborsToCellList(Maze maze, PriorityQueue<CellEdge> cellList, int x, int y) {
        int[] currPos = new int[]{x, y};
        for (int[] neighbor : getNeighbors(maze, x, y, false)) {
            if (maze.isInBounds( neighbor[0], neighbor[1] )) {
                cellList.add(new CellEdge(currPos, neighbor,
                        Math.random() * 100)); // Assign random weights
            }
        }
    }
    @Override
    public void generate(Maze maze) {
        PriorityQueue<CellEdge> cellList = new PriorityQueue<>(Comparator.comparingDouble(a -> a.weight));

        int startX = 0;
        int startY = 0;

        maze.setWall( startX, startY, false);
        addNeighborsToCellList(maze, cellList, startX, startY); // Add the initial neighbors

        while (!cellList.isEmpty()) {
            // Randomly pick an edge from the list
            CellEdge cellEdge = cellList.poll();

            int[] from = cellEdge.from, cell = cellEdge.cell;
            if (! maze.hasWall( cell[0], cell[1] ) )
                continue;

            createPath(maze, from, cell);

            // Add new neighbors to the list
            addNeighborsToCellList(maze, cellList, cell[0], cell[1]);
        }
    }
}
