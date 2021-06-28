package cassgame;

import com.datastax.driver.core.*;

public class Utils {

    public static int[] getConfig(Session session) {
        String getConfig = "SELECT * FROM pong_cassandra.Config WHERE config == 'config';";
        ResultSet rsGetConf = session.execute(getConfig);
        Row row_get_conf = rsGetConf.one();
        return new int[]{
                row_get_conf.getList("params", int.class).get(0),
                row_get_conf.getList("params", int.class).get(1),
                row_get_conf.getList("params", int.class).get(2),
                row_get_conf.getList("params", int.class).get(3)
        };
    }

    public static int[] getPositions(Session session) {

        String getPositions = "SELECT * FROM pong_cassandra.Positions WHERE pos == 'pos';";
        ResultSet rsGetPos = session.execute(getPositions);
        Row row_get_pos = rsGetPos.one();
        return new int[]{
                row_get_pos.getList("ball", int.class).get(0),
                row_get_pos.getList("ball", int.class).get(1),
                row_get_pos.getInt("player1"),
                row_get_pos.getInt("player2")
        };
    }

    public static int getPlayerPosition(Session session, int playerID) {
        String getPosition;
        if (playerID == 0) {
            getPosition = "SELECT player1 FROM pong_cassandra.Positions WHERE pos == 'pos';";
        } else {
            getPosition = "SELECT player2 FROM pong_cassandra.Positions WHERE pos == 'pos';";
        }
        ResultSet rsGetPos = session.execute(getPosition);
        Row row_get_pos = rsGetPos.one();
        if(playerID == 0) {
            return row_get_pos.getInt("player1");
        }
        else {
            return row_get_pos.getInt("player2");
        }
    }

}
