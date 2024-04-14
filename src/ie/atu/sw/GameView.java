package ie.atu.sw;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.ThreadLocalRandom.current;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.swing.Timer;

import jhealy.aicme4j.net.NeuralNetwork;
import jhealy.aicme4j.net.Output;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.LinkedList;

import javax.swing.JPanel;
import javax.swing.Timer;

public class GameView extends JPanel implements ActionListener{
	//Some constants
	private static final long serialVersionUID	= 1L;
	private static final int MODEL_WIDTH 		= 30;
	private static final int MODEL_HEIGHT 		= 20;
	private static final int SCALING_FACTOR 	= 30;
	
	private static final int MIN_TOP 			= 2;
	private static final int MIN_BOTTOM 		= 18;
	private static final int PLAYER_COLUMN 		= 15;
	private static final int TIMER_INTERVAL 	= 100;
	
	private static final byte ONE_SET 			=  1;
	private static final byte ZERO_SET 			=  0;
	
	private Timer logDataTimer;
	private int lastMove = 0;
	private NeuralNetwork net;


	/*
	 * The 30x20 game grid is implemented using a linked list of 
	 * 30 elements, where each element contains a byte[] of size 20. 
	 */
	private LinkedList<byte[]> model = new LinkedList<>();

	//These two variables are used by the cavern generator. 
	private int prevTop = MIN_TOP;
	private int prevBot = MIN_BOTTOM;
	
	//Once the timer stops, the game is over
	private Timer timer;
	private long time;
	
	private int playerRow = 11;
	private int index = MODEL_WIDTH - 1; //Start generating at the end
	private Dimension dim;
	
	//Some fonts for the UI display
	private Font font = new Font ("Dialog", Font.BOLD, 50);
	private Font over = new Font ("Dialog", Font.BOLD, 100);

	//The player and a sprite for an exploding plane
	private Sprite sprite;
	private Sprite dyingSprite;
	
	private boolean auto;

	public GameView(boolean auto) throws Exception{
		
		this.auto = auto; //Use the autopilot
		
		setDoubleBuffered(true);
	    initNeuralNetwork();
		
		setBackground(Color.LIGHT_GRAY);
		setDoubleBuffered(true);
		
		//Creates a viewing area of 900 x 600 pixels
		dim = new Dimension(MODEL_WIDTH * SCALING_FACTOR, MODEL_HEIGHT * SCALING_FACTOR);
    	super.setPreferredSize(dim);
    	super.setMinimumSize(dim);
    	super.setMaximumSize(dim);
		
    	initModel();
    	
		timer = new Timer(TIMER_INTERVAL, this); //Timer calls actionPerformed() every second
		timer.start();
		
		// Timer to log data every three seconds
        logDataTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double[][] sampledSection = sample();
                logData(sampledSection, lastMove);
                resetLastMove();
            }
        });
        
	}
	
	//Build our game grid
	private void initModel() {
		for (int i = 0; i < MODEL_WIDTH; i++) {
			model.add(new byte[MODEL_HEIGHT]);
		}
	}
	
	public void setSprite(Sprite s) {
		this.sprite = s;
	}
	
	public void setDyingSprite(Sprite s) {
		this.dyingSprite = s;
	}
	
	// Method to get the game model data
    public LinkedList<byte[]> getModel() {
        return model;
    }
	
	//Called every second by actionPerformed(). Paint methods are usually ugly.
	public void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2 = (Graphics2D)g;
        
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, dim.width, dim.height);
        
        int x1 = 0, y1 = 0;
        for (int x = 0; x < MODEL_WIDTH; x++) {
        	for (int y = 0; y < MODEL_HEIGHT; y++){  
    			x1 = x * SCALING_FACTOR;
        		y1 = y * SCALING_FACTOR;

        		if (model.get(x)[y] != 0) {
            		if (y == playerRow && x == PLAYER_COLUMN) {
            			timer.stop(); //Crash...
            		}
            		g2.setColor(Color.BLACK);
            		g2.fillRect(x1, y1, SCALING_FACTOR, SCALING_FACTOR);
        		}
        		
        		if (x == PLAYER_COLUMN && y == playerRow) {
        			if (timer.isRunning()) {
            			g2.drawImage(sprite.getNext(), x1, y1, null);
        			}else {
            			g2.drawImage(dyingSprite.getNext(), x1, y1, null);
        			}
        			
        		}
        	}
        }
        
        /*
         * Not pretty, but good enough for this project... The compiler will
         * tidy up and optimise all of the arithmetics with constants below.
         */
        g2.setFont(font);
        g2.setColor(Color.RED);
        g2.fillRect(1 * SCALING_FACTOR, 15 * SCALING_FACTOR, 400, 3 * SCALING_FACTOR);
        g2.setColor(Color.WHITE);
        g2.drawString("Time: " + (int)(time * (TIMER_INTERVAL/1000.0d)) + "s", 1 * SCALING_FACTOR + 10, (15 * SCALING_FACTOR) + (2 * SCALING_FACTOR));
        
        if (!timer.isRunning()) {
			g2.setFont(over);
			g2.setColor(Color.RED);
			g2.drawString("Game Over!", MODEL_WIDTH / 5 * SCALING_FACTOR, MODEL_HEIGHT / 2* SCALING_FACTOR);
        }
	}
	
	
	
	
	public void logData(double[][] sample, int lastMove) {
	    try {
	        // File path 
	        File file = new File("./Resources/game_data.txt");
	        
	        // Initializing PrintWriter and setting append mode to true
	        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
	            
	            // Writing the sampled section 3x3 (MapData) to the file
	            for (int row = 0; row < sample.length; row++) {
	                for (int col = 0; col < sample[row].length; col++) {
	                    pw.print(sample[row][col] + " ");
	                }
	                pw.println();
	            }
	            pw.println("---"); 
	            pw.println(lastMove);
	            pw.println();
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    logDataTimer.start();
	}


	//Move the plane up or down
	public void move(int step) {
	    playerRow += step;
	    //This is for recording the last move during the session
	    lastMove = step;
	}
	
	//Calling it for the Gamewindow
	public int getLastMove() {
	    return lastMove;
	}
	
	//So it doesnt stick with -1 or 1 after being pressed
	public void resetLastMove() {
	    lastMove = 0;
	}
	
	
	/*
	 * ----------
	 * AUTOPILOT!
	 * ----------
	 * The following implementation randomly picks a -1, 0, 1 to control the plane. You 
	 * should plug the trained neural network in here. This method is called by the timer
	 * every TIMER_INTERVAL units of time from actionPerformed(). There are other ways of
	 * wiring your neural network into the application, but this way might be the easiest. 
	 *  
	 */
	
	private double[] flatten(double[][] input) {
	    return Arrays.stream(input)
	                 .flatMapToDouble(Arrays::stream)
	                 .toArray();
	}
	
	private void initNeuralNetwork() {
	    try {
	        ModelTrainer trainer = new ModelTrainer();
	        trainer.go(); //trains the network
	        this.net = trainer.getNet(); // Access the trained network
	        System.out.println("Network initialized successfully.");
	    } catch (Exception e) {
	        e.printStackTrace();
	        System.out.println("Failed to initialize the neural network");
	        throw new RuntimeException("Failed to initialize the neural network", e);
	    }
	}
	
	
	private void autoMove() {
	    double[][] gameState = sample();
	    double[] flattenedGameState = flatten(gameState);
	    
	    try {
	        double predicted = net.process(flattenedGameState, Output.NUMERIC);
	        int move = interpretOutput(predicted); 
	        move(move);
	        System.out.println(predicted);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

	// Taking in interpretation of prediction 
	private int interpretOutput(double prediction) {
	    return (int)Math.round(prediction); // Map prediction to -1, 0, 1.
	}

	
	//Called every second by the timer 
	public void actionPerformed(ActionEvent e) {
	    time++;
	    
	    // Update our timer
	    this.repaint(); // Repaint the cavern

	    // Update the next index to generate
	    index++;
	    index = (index == MODEL_WIDTH) ? 0 : index;

	    generateNext(); // Generate the next part of the cave
	    if (auto) autoMove();

	    
	}
	
	
	/*
	 * Generate the next layer of the cavern. Use the linked list to
	 * move the current head element to the tail and then randomly
	 * decide whether to increase or decrease the cavern. 
	 */
	private void generateNext() {
		var next = model.pollFirst(); 
		model.addLast(next); //Move the head to the tail
		Arrays.fill(next, ONE_SET); //Fill everything in
		
		
		//Flip a coin to determine if we could grow or shrink the cave
		var minspace = 4; //Smaller values will create a cave with smaller spaces
		prevTop += current().nextBoolean() ? 1 : -1; 
		prevBot += current().nextBoolean() ? 1 : -1;
		prevTop = max(MIN_TOP, min(prevTop, prevBot - minspace)); 		
		prevBot = min(MIN_BOTTOM, max(prevBot, prevTop + minspace));

		//Fill in the array with the carved area
		Arrays.fill(next, prevTop, prevBot, ZERO_SET);
	}
	
	
	/*
	 * Use this method to get a snapshot of the 30x20 matrix of values
	 * that make up the game grid. The grid is flatmapped into a single
	 * dimension double array... (somewhat) ready to be used by a neural 
	 * net. You can experiment around with how much of this you actually
	 * will need. The plane is always somehere in column PLAYER_COLUMN
	 * and you probably do not need any of the columns behind this. You
	 * can consider all of the columns ahead of PLAYER_COLUMN as your
	 * horizon and this value can be reduced to save space and time if
	 * needed, e.g. just look 1, 2 or 3 columns ahead. 
	 * 
	 * You may also want to track the last player movement, i.e.
	 * up, down or no change. Depending on how you design your neural
	 * network, you may also want to label the data as either okay or 
	 * dead. Alternatively, the label might be the movement (up, down
	 * or straight). 
	 *  
	 */
	public double[][] sample() {
	    // Variables for the 3x3 grid
	    int rowsToSample = 3;
	    int columnsToSample = 3;
	    double[][] section = new double[rowsToSample][columnsToSample];

	    // Starting row
	    int startRow = Math.max(0, playerRow - 1);

	    // so i dont go out of bounds 
	    if (startRow + rowsToSample > MODEL_HEIGHT) {
	        startRow = MODEL_HEIGHT - rowsToSample;
	    }

	    // This for making the column 3x3
	    for (int col = 0; col < columnsToSample; col++) {
	        int modelIndex = (PLAYER_COLUMN + col + 1) % MODEL_WIDTH; // Ensure wrapping around the model
	        for (int row = 0; row < rowsToSample; row++) {
	            // Adjust row index to match the starting row
	            int adjustedRow = startRow + row;
	            section[row][col] = model.get(modelIndex)[adjustedRow];
	        }
	    }
	    return section;
	}

	
	
	/*
	 * Resets and restarts the game when the "S" key is pressed
	 */
	public void reset() {
		model.stream() 		//Zero out the grid
		     .forEach(n -> Arrays.fill(n, 0, n.length, ZERO_SET));
		playerRow = 11;		//Centre the plane
		time = 0; 			//Reset the clock
		timer.restart();	//Start the animation
	}
}