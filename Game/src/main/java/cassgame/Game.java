package cassgame;

import com.datastax.driver.core.*;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;


// Lewy górny róg na 0, 0
// Zapisywać do bazy kto wygrał: ball_pos: jeśli x = -1 to przegrał gracz 0, jeśli y = -1 to gracz 1
// Tworzenie tabel na początku Game (if not exist) i wstawianie wartości poczatkowych
// ball_pos konwertować na inty przy zapisywaniu do cassandry
// Nieparzysta batwidth


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
        // Check if player lost when ball reached his boundary
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
                return new double[]{-1, -1, 0};
            }
            else {
                // Ball bounces back from left bat
                newBallPosition = new double[]{intersection.getX(), intersection.getY(), Math.PI+direction};
            }

        }
        else if(newPos[0] > boardSize[0]) {
            Line2D ballTrajectory = new Line2D.Double(ballPos[0], ballPos[1], newPos[0], newPos[1]);
            Line2D rightPlayerBoundary = new Line2D.Double(boardSize[0], 0, boardSize[0], boardSize[1]);
            Point2D intersection = getIntersection(ballTrajectory, rightPlayerBoundary);
            boolean lost = checkIfLoser(intersection, batWidth, 1, session);
            if(lost) {
                // Player 1 loses
                return new double[]{-1, -1, 1};
            }
            else {
                // Ball bounces back from right bat
                newBallPosition = new double[]{intersection.getX(), intersection.getY(), Math.PI+direction};
            }
        }
        else {
            // Ball keeps going with no obstacles
            newBallPosition = new double[]{newPos[0], newPos[1], direction};
        }

        // Update Cassandra
        // Poprawic zeby zapisywac jako int a nie double
        // Zapisywac kto przegral
        PreparedStatement updatePosition = session.prepare("UPDATE pong_cassandra.Positions SET ball = [?, ?] WHERE pos = 'pos';")
                .setConsistencyLevel(ConsistencyLevel.ANY);
        BoundStatement boundUpdate = updatePosition.bind(newBallPosition[0], newBallPosition[1]);
        ResultSet rsUpdate = session.execute(boundUpdate);
        Row row_update = rsUpdate.one();
        if(!row_update.getBool("[applied]")) {
            System.out.println("Falied to update Cassandra");
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

        Cluster cluster = Cluster.builder().addContactPoint("172.18.0.2").build();
        Session session = cluster.connect();

        // Initial configuration
        double[] config = Utils.getConfig(session);
        double[] positions = Utils.getPositions(session);
        boardSize = new double[]{config[0], config[1]};
        dt = (int)config[2];
        batWidth = config[3];
        ballPos = new double[]{positions[0], positions[1]};
        direction = Math.random()*2*Math.PI;

        // Main game loop
        while(true) {
            double[] newCoordinates = updateBallPosition(dt, ballPos, direction, vel, boardSize, batWidth, session);
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
