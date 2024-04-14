package ie.atu.sw;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import jhealy.aicme4j.NetworkBuilderFactory;
import jhealy.aicme4j.net.Activation;
import jhealy.aicme4j.net.Loss;
import jhealy.aicme4j.net.NeuralNetwork;
import jhealy.aicme4j.net.Output;

public class ModelTrainer {
	
	private List<double[]> inputData = new ArrayList<>();
    private List<double[]> outputData = new ArrayList<>();
    
    private NeuralNetwork net;
    
     
    public void go() throws Exception {
        
    	loadTxt("./Resources/game_data.txt");

        double[][] data = inputData.toArray(new double[0][]);
        double[][] expected = outputData.toArray(new double[0][]);

        this.net = NetworkBuilderFactory.getInstance().newNetworkBuilder()
                .inputLayer("Input", 9)
                .hiddenLayer("Hidden1", Activation.TANH, 7) 
                .outputLayer("Output", Activation.TANH, expected[0].length) 
                .train(data, expected, 0.001, 0.9, 60000, 0.000001, Loss.SSE)
                .save("./Resources/plane_control.data")
                .build();
        
        System.out.println(net);
        double[] testScenario = data[0]; 
        var predicted = net.process(testScenario, Output.NUMERIC_ROUNDED);
        System.out.println("Test Scenario => Predicted: " + predicted);
    }
    
    public NeuralNetwork getNet() {
        return this.net;
    }
    

    private void loadTxt(String filePath) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            List<Double> currentMapData = new ArrayList<>();
            boolean readingMapData = true;

            while ((line = br.readLine()) != null) {
                line = line.trim(); 
                if (line.isEmpty()) {
                    continue; // Skip empty spaces
                }

                if (line.equals("---")) {
                    readingMapData = false;
                    continue;
                }

                if (readingMapData) {
                    // Split line into values
                    for (String value : line.split("\\s+")) {
                        value = value.trim(); 
                        if (!value.isEmpty()) { 
                            currentMapData.add(Double.parseDouble(value));
                        }
                    }
                } else {
                    
                    double[] move = new double[]{Double.parseDouble(line)};
                    double[] mapDataArray = currentMapData.stream().mapToDouble(Double::doubleValue).toArray();
                    inputData.add(mapDataArray);
                    outputData.add(move);

                    currentMapData.clear(); //reset
                    readingMapData = true; 
                }
            }
        }
    }



    public static void main(String[] args) throws Exception {
        new ModelTrainer().go();
    }
}
