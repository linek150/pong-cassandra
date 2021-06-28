package cassgame

import java.lang.Math;
import java.util.Timer;
import java.io.IOException;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import java.util.Timer;
import java.util.TimerTask;


class Player extends JFrame implements KeyListener{
    static final int N=20;
    static final int WIDTH=0;
    static final int HEIGHT=1;
    static private boolean keyPressed=false;
    static private int playerID=0;
    static private int patWidth=5;
    static private int myPosition=N/2;
    static private int opPosition=N/2;
    static private int dt=100;
    static private int platerID;
    static private int ballPosition[] = {4*N/2,N/2};
    static private int boardSize[] = {4*N+1,N};
    Player(){
        addKeyListener(this);
        setSize(50,50);
        setVisible(true);
    }
    static private void getConfigCass(){
        System.out.println("CASSANDRA");
    }
    static void drawBoard(){
        System.out.print("\033[H\033[2J");  
        for(int h =0;h<boardSize[HEIGHT];h++ ){
            for(int w=0;w<boardSize[WIDTH];w++ ){
                //left edge of screen
                if(w==0 && ((playerID==0 && Math.abs(h-myPosition)<=patWidth/2) || (playerID==1 && Math.abs(h-opPosition)<=patWidth/2))){
                    System.out.print("|");
                }//rigth edge of screen
                else if(w==boardSize[WIDTH]-1 && ((playerID==1 && Math.abs(h-myPosition)<=patWidth/2) || (playerID==0 && Math.abs(h-opPosition)<=patWidth/2))){
                    System.out.print("|"); 
                }
                else if(w==ballPosition[WIDTH] && h==ballPosition[HEIGHT]){
                    System.out.print("*");
                }else{
                    System.out.print("\s");
                }
               
            }
            System.out.print("\n"); 
        }
        System.out.flush(); 
    }
    public void keyPressed(KeyEvent e){
        if(!(keyPressed)){
            makeMove(e.getKeyCode());
            keyPressed=true;
        }
    }
    public void keyReleased(KeyEvent e){keyPressed=false;}
    public void keyTyped(KeyEvent e){}
    private void makeMove(int key){
        switch(key){
            case KeyEvent.VK_UP:
                myPosition--;
            break;
            case KeyEvent.VK_DOWN:
                myPosition++;
            break;
            case KeyEvent.VK_ESCAPE:
                dispose();

                System.exit(1);
            break;
        }
    }

    public static void main(String[] args){
        Player.getConfigCass();
        Player player=new Player();
        Timer drawTimer=new Timer();
        drawTimer.schedule(new TimerTask(){
            public void run(){Player.drawBoard();}
        }, 0,200);
        while(true){}
    }
}