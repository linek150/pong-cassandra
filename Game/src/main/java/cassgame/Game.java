package cassgame;

import com.datastax.driver.core.*;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;


public class Game {

    public static double[] calculateBallPosition(int dt, double[] ballPos, double direction, int vel) {
        // Calculate new ball position for a given time step
        double dr = vel*dt;
        double dx = dr*Math.cos(direction);
        double dy = dr*Math.sin(direction);
        return new double[]{ballPos[0] + dx, ballPos[1] + dy};
    }

    public static Point2D getIntersection(Line2D a, Line2D b) {
        // Get intersection of ball trajectory and boundary
        double x1 = a.getX1(), y1 = a.getY1(), x2 = a.getX2(), y2 = a.getY2(), x3 = b.getX1(), y3 = b.getY1(),
                x4 = b.getX2(), y4 = b.getY2();
        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (d == 0) {
            return null;
        }

        double xi = ((x3 - x4) * (x1 * y2 - y1 * x2) - (x1 - x2) * (x3 * y4 - y3 * x4)) / d;
        double yi = ((y3 - y4) * (x1 * y2 - y1 * x2) - (y1 - y2) * (x3 * y4 - y3 * x4)) / d;

        return new Point2D.Double(xi, yi);
    }

    public static boolean checkIfLoser(Point2D intersection, double batWidth, int player, Session session) {
        // Check if player lost when ball reached his boundary (read his position from Cassandra)
        int playerPos = Utils.getPlayerPosition(session, player);
        double[] playerRange = new double[]{playerPos - (batWidth-1)/2, playerPos + (batWidth-1)/2};
        if(intersection.getY() < playerRange[0] || intersection.getY() > playerRange[1]) {
            return true;
        }
        else {
            return false;
        }
    }

    public static double[] updateBallPosition(
            int dt,
            double[] ballPos,
            double direction,
            int vel,
            double[] boardSize,
            double batWidth,
            Session session)
    {
        // Calculate new ball position and check for losers
        double[] newBallPosition;

        double[] newPos = calculateBallPosition(dt, ballPos, direction, vel);
        while(newPos[1] < 0 || newPos[1] > boardSize[1]) {
            // Avoid crossing upper and lower boundary (bounce in random direction)
            direction = Math.random()*2*Math.PI;
            newPos = calculateBallPosition(dt, ballPos, direction, vel);
        }

        if(newPos[0] < 0) {
            Line2D ballTrajectory = new Line2D.Double(ballPos[0], ballPos[1], newPos[0], newPos[1]);
            Line2D leftPlayerBoundary = new Line2D.Double(0, 0, 0, boardSize[1]);
            Point2D intersection = getIntersection(ballTrajectory, leftPlayerBoundary);
            boolean lost = checkIfLoser(intersection, batWidth, 0, session);
            if(lost) {
                // Player 0 loses
                newBallPosition = new double[]{-1, -1, 0};
            }
            else {
                // Ball bounces back from left bat
                newBallPosition = new double[]{intersection.getX(), intersection.getY(), Math.PI+direction};
                System.out.println("Player 0 bounces back!");
            }

        }
        else if(newPos[0] > boardSize[0]) {
            Line2D ballTrajectory = new Line2D.Double(ballPos[0], ballPos[1], newPos[0], newPos[1]);
            Line2D rightPlayerBoundary = new Line2D.Double(boardSize[0], 0, boardSize[0], boardSize[1]);
            Point2D intersection = getIntersection(ballTrajectory, rightPlayerBoundary);
            boolean lost = checkIfLoser(intersection, batWidth, 1, session);
            if(lost) {
                // Player 1 loses
                newBallPosition = new double[]{-1, -1, 1};
            }
            else {
                // Ball bounces back from right bat
                newBallPosition = new double[]{intersection.getX(), intersection.getY(), Math.PI+direction};
                System.out.println("Player 1 bounces back!");
            }
        }
        else {
            // Ball keeps going with no obstacles
            newBallPosition = new double[]{newPos[0], newPos[1], direction};
        }

        // Update Cassandra
        PreparedStatement updatePosition = session.prepare("UPDATE pong_cassandra.Positions SET ball = [?, ?] WHERE pos = 'pos';")
                .setConsistencyLevel(ConsistencyLevel.ANY);
        BoundStatement boundUpdate;
        if(newBallPosition[0] == -1) {
            if(newBallPosition[2] == 0) {
                boundUpdate = updatePosition.bind(-1, 0);
            }
            else {
                boundUpdate = updatePosition.bind(0, -1);
            }
        }
        else {
            boundUpdate = updatePosition.bind((int)newBallPosition[0], (int)newBallPosition[1]);
        }
        ResultSet rsUpdate = session.execute(boundUpdate);
        Row row_update = rsUpdate.one();
        if(!row_update.getBool("[applied]")) {
            System.out.println("Failed to update Cassandra");
        }

        return newBallPosition;
    }

    public static void main(String[] args) throws InterruptedException {

        int vel = 10;
        double[] boardSize;
        double[] ballPos;
        double direction;
        int dt;
        double batWidth;

        // Cassandra code
        Cluster cluster = Cluster.builder().addContactPoint("172.18.0.2").build();
        Session session = cluster.connect();

        String createConfig = "CREATE TABLE IF NOT EXISTS pong_cassandra.Config(\n" +
                "   config text PRIMARY KEY,\n" +
                "   params list<int>\n" +
                "   );";
        ResultSet rsCreate = session.execute(createConfig);
        Row row_create = rsCreate.one();
        if(!row_create.getBool("[applied]")) {
            System.out.println("Failed to create Cassandra table");
        }

        String insertConfig = "INSERT INTO pong_cassandra.Config (config, params)"
                + " VALUES('config', [300, 200, 1, 11]);";
        ResultSet rsInsert = session.execute(insertConfig);
        Row row_insert = rsInsert.one();
        if(!row_insert.getBool("[applied]")) {
            System.out.println("Failed to insert into Cassandra table");
        }

        String createPositions = "CREATE TABLE IF NOT EXISTS pong_cassandra.Positions(\n" +
                "   pos text PRIMARY KEY,\n" +
                "   ball list<int>,\n" +
                "   player1 int,\n" +
                "   player2 int\n" +
                "   );";
        ResultSet rsCreatePos = session.execute(createPositions);
        Row row_create_pos = rsCreatePos.one();
        if(!row_create_pos.getBool("[applied]")) {
            System.out.println("Failed to create Cassandra table");
        }

        String insertPositions = "INSERT INTO pong_cassandra.Positions (pos, ball, player1, player2)"
                + " VALUES('pos', [150, 100], 100, 100);";
        ResultSet rsInsertPos = session.execute(insertPositions);
        Row row_insert_pos = rsInsertPos.one();
        if(!row_insert_pos.getBool("[applied]")) {
            System.out.println("Failed to insert into Cassandra table");
        }

        // Initial configuration
        boardSize = new double[]{300, 200};
        dt = 1;
        batWidth = 11;
        ballPos = new double[]{150, 100};
        direction = Math.random()*2*Math.PI;

        // Main game loop
        while(true) {

            // If using Cassandra
            double[] newCoordinates = updateBallPosition(dt, ballPos, direction, vel, boardSize, batWidth, session);
            // If not using Cassandra
//            double[] newCoordinates = updateBallPosition(dt, ballPos, direction, vel, boardSize, batWidth, null);

            System.out.println("Ball is at: "+Double.toString(newCoordinates[0])+" "+Double.toString(newCoordinates[1]));

            if(newCoordinates[0] == -1) {
                System.out.println("Player ".concat(String.valueOf(newCoordinates[2])).concat(" loses!"));
                break;
            }
            else {
                ballPos = new double[]{newCoordinates[0], newCoordinates[1]};
                direction = newCoordinates[2];
                TimeUnit.SECONDS.sleep(dt);
            }
        }
    }
}
