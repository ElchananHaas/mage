package mage.player.ai.RLAgent;


import mage.game.Game;
import mage.players.Player;
import mage.util.RandomUtil;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;

import java.io.Serializable;
import java.util.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.EmbeddingSequenceLayer;
import org.deeplearning4j.nn.conf.layers.GlobalPoolingLayer;
import org.deeplearning4j.nn.conf.layers.Convolution1D;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex;
import org.deeplearning4j.nn.conf.graph.ReshapeVertex;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex.Op;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.deeplearning4j.nn.conf.layers.ActivationLayer;
import org.deeplearning4j.optimize.listeners.CollectScoresIterationListener;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;


/**
 * @author Elchanan Haas
 */

//Main learner 
//Takes in a game state and a list of RLAction and can choose an action
//Records its experiences and can learn from them 
public class RLLearner implements Serializable{
    //private static final Logger logger = Logger.getLogger(RLLearner.class);
    static final long serialVersionUID=1L;
    public transient LinkedList<GameSequence> games; //Save seperately because it is big
    public transient CollectScoresIterationListener losses;
    private double epsilon=.5f; //For epsilon greedy learning. Set to 0 for evaluation
    protected boolean evaluateMode; //Sets mode to testing, no experience should be collected  
    Representer representer;
    public transient ComputationGraph model; //Must be serialized seperately with its own methods
    protected transient ComputationGraph targetNet;
    //needs to be public for serialization
    HParams hparams;
    //Constructor--creates a RLLearner
    public RLLearner(){
        games=new LinkedList<GameSequence>(); 
        model=constructModel();
        
        representer=new Representer();
        copyToTarget();
        evaluateMode=false;
        hparams=new HParams();
        //prevents logging spam
        //Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        //root.setLevel(Level.INFO);
    }
    //Sets evalueate mode. When evaluate mode is 
    //True, no experience is collected
    public void setEvaluateMode(Boolean isEvaluateMode){
        evaluateMode=isEvaluateMode;
    }
    public void copyToTarget(){
        if(Objects.nonNull(targetNet)){
            targetNet.close();
        }
        targetNet=model.clone();
    }
    //Epsilon controlls the tradeoff between random
    //and greedy actions, lower values being more greedy
    //Prerequisites: epsilon in [0_1]
    public void setEpsilon(float eps){
        epsilon=eps;
    }
    //Creates a new GameSequence and adds it to the list
    //to later learn from. This game will be returned from
    //getCurrentGame 
    public void newGame(Player player){
        games.add(new GameSequence());
    }
    //Decides which games to sample experiences from, the exact
    //experience is chosen later
    protected List<Integer> sampleGames(int size){
        List<Integer> sizes=new ArrayList<Integer>();
        int totalSize=0;
        for(GameSequence game:games){
            int gameSize=game.experiences.size();
            sizes.add(gameSize);
            totalSize+=gameSize;
        }
        List<Integer> gamesToSample=new ArrayList<Integer>();
        for(int i=0;i<size;i++){
            int expIndex=RandomUtil.nextInt(totalSize);
            int count=0;
            while(expIndex>=sizes.get(count)){
                expIndex-=sizes.get(count);
                count+=1;
            }
            gamesToSample.add(count);
        }
        return gamesToSample;
    }
    //Represents a batch of data as a list of INDArrays
    //Remember--this simply doens't represent NULL elements
    //Only getTargets can currently handle NULL elements
    protected List<INDArray> represent_batch(List<RepresentedGame> reprGames){
        List<List<INDArray>> prePiled=new ArrayList<List<INDArray>>();
        for(int i=0;i<hparams.total_model_inputs;i++){
            prePiled.add(new ArrayList<INDArray>());
        }
        for(int i=0;i<reprGames.size();i++){
            INDArray[] repr=reprGames.get(i).asModelInputs();
            for(int j=0;j<hparams.total_model_inputs;j++){
                prePiled.get(j).add(repr[j]);
            }
        }
        List<INDArray> batchRepr=new ArrayList<INDArray>();
        //Debugging code for testing mismatched zero vs. real reprs
        /*for(int i=0;i<prePiled.size();i++){
            for(int j=0;j<prePiled.get(i).size();j++){
                String s1=prePiled.get(i).get(j).shapeInfoToString();
                String s2=prePiled.get(i).get(0).shapeInfoToString();
                if(!s1.equals(s2)){
                    logger.info(i+"\n"+s1+"\n"+s2+"\n---\n");
                }
            }
        }*/
        for(int i=0;i<prePiled.size();i++){
            
            INDArray piled=Nd4j.pile(prePiled.get(i));
            INDArray dataCol=Nd4j.squeeze(piled,1);
            batchRepr.add(dataCol);
        }
        return batchRepr;
    } 
    //Runs the model on given inputs
    protected INDArray runModel(ComputationGraph graph, List<RepresentedGame> reprGames,INDArray mask){
        List<INDArray> representedExps=represent_batch(reprGames);
        representedExps.add(mask);
        Map<String,INDArray> qmodelOut=graph.feedForward(representedExps.toArray(new INDArray[representedExps.size()]),true);
        INDArray QValues=qmodelOut.get("linout").reshape(-1,hparams.max_representable_actions);
        return QValues;
    }
    //Calculates the targets of the ML model for the actions taken
    protected List<Double> getTargets(List<GameSequence> sampledGames,List<RepresentedGame> currentReprs, List<RepresentedGame> nextReprs){
        INDArray QValues;
        INDArray mask=Nd4j.ones(hparams.batch_size,1,hparams.max_representable_actions);
        if(hparams.double_dqn){
            QValues=runModel(targetNet,nextReprs,mask);
        }
        else{
            QValues=runModel(model,nextReprs,mask);
        }
        INDArray maxQ=QValues.max(1);
        List<Double> targets=new ArrayList<Double>();
        for(int i=0;i<nextReprs.size();i++){
            if(!nextReprs.get(i).isDone){
                targets.add(maxQ.getDouble(i)*hparams.discount);
            }
            else{
                //logger.info("adding game win/loss "+sampledGames.get(i).getValue());
                double value=(double) sampledGames.get(i).getValue();
                targets.add(value);
            }
        }
        return targets;
    }
    //Trains the ML model on a batch of data of the given size
    //Past the end of the game, the next state is represented as NULL
    public void trainBatch(int size){
        List<Integer> gamesToSample=sampleGames(size);
        List<GameSequence> sampledGames=new ArrayList<GameSequence>();
        List<Experience> currentExps=new ArrayList<Experience>();
        List<RepresentedGame> currentReprs=new ArrayList<RepresentedGame>();
        List<RepresentedGame> nextReprs=new ArrayList<RepresentedGame>();
        for(int i=0;i<size;i++){
            GameSequence sampledGame=games.get(gamesToSample.get(i));
            sampledGames.add(sampledGame);
            int expIndex=RandomUtil.nextInt(sampledGame.experiences.size());
            Experience exp=sampledGame.experiences.get(expIndex);
            currentExps.add(exp);
            currentReprs.add(exp.repr);
            if(expIndex+1 != sampledGame.experiences.size()){
                nextReprs.add(sampledGame.experiences.get(expIndex+1).repr);
            }
            else{
                nextReprs.add(representer.emptyGame());
            }
        }
        INDArray target=Nd4j.zeros(hparams.batch_size,1,hparams.max_representable_actions);
        INDArray mask=Nd4j.zeros(hparams.batch_size,1,hparams.max_representable_actions);
        List<Double> targets=getTargets(sampledGames, currentReprs,nextReprs);
        //logger.info(targets.toString());
        for(int i=0;i<size;i++){
            target.putScalar(i,0,currentExps.get(i).chosen,targets.get(i));
            mask.putScalar(i,0,currentExps.get(i).chosen,1.0);
        }
        
        //target=Nd4j.expandDims(target, 1);
        List<INDArray> representedExps=represent_batch(currentReprs);
        representedExps.add(mask);
        INDArray[] inputs=representedExps.toArray(new INDArray[representedExps.size()]);
        model.fit(inputs, new INDArray[]{target});
    } 
    //Gets the action the model thinks is best in this game state
    protected int getGreedy(RepresentedGame repr){
        INDArray inputs[]=new INDArray[hparams.total_model_inputs+1];
        System.arraycopy(repr.asModelInputs(),0,inputs,0,hparams.total_model_inputs);
        inputs[hparams.total_model_inputs]=Nd4j.ones(1,hparams.max_representable_actions);
        Map<String,INDArray> qmodelOut;
        if(hparams.double_dqn){
            qmodelOut=targetNet.feedForward(inputs,false);
        }else{
            qmodelOut=model.feedForward(inputs,false);
        }
        
        INDArray q_values=qmodelOut.get("linout").reshape(hparams.max_representable_actions);
        double max_q=Double.NEGATIVE_INFINITY;
        int max_index=0;
        for(int i=0;i<Math.min(repr.numActions,hparams.max_representable_actions);i++){
            double value=q_values.getDouble(i);
            if(value>max_q){
                max_q=value;
                max_index=i;
            }
        }
        return max_index;
    }
    //Rund the epsilon greedy algorithm
    public int choose(Game game, Player player,List<RLAction> actions){
        int choice;
        
        /*if(1<2){
        return RandomUtil.nextInt(actions.size());
        }*/
        RepresentedGame repr=representer.represent(game, player, actions);
        if(RandomUtil.nextDouble()<epsilon){//random action
            choice=RandomUtil.nextInt(Math.min(actions.size(),hparams.max_representable_actions));
        }
        else{//Greedy action from q learner
            choice=getGreedy(repr);
        }
        if(!evaluateMode){
            Experience exp=new Experience(repr,choice);
            getCurrentGame().addExperience(exp);
        }
        return choice;
    }
    //Constructs the model. Should only be run once in constructor,
    //because it sets the training listener
    ComputationGraph constructModel(){
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
        .updater(new Adam(hparams.lr))
        .graphBuilder()
        .addInputs("actionIDs","otherReal","permanentIDs","extraPermInfo","mask") //can use any label for this        
        .addLayer("embedPermanent", new EmbeddingSequenceLayer.Builder().nIn(hparams.max_represents)
          .nOut(hparams.internal_dim).inputLength(hparams.max_representable_permanents).build(),"permanentIDs")
        .addVertex("addExtraInfo",new MergeVertex(),"embedPermanent","extraPermInfo")
        .addLayer("poolPermanent", new GlobalPoolingLayer.Builder(PoolingType.SUM).build(),"addExtraInfo")
        .addLayer("normPool",new BatchNormalization.Builder().nIn(hparams.internal_dim+hparams.perm_features)
        .nOut(hparams.internal_dim+hparams.perm_features).build(),"poolPermanent")
        .addLayer("combinedGame", new DenseLayer.Builder().nIn(hparams.game_reals+hparams.internal_dim+hparams.perm_features)
          .nOut(hparams.internal_dim).activation(Activation.RELU).build(), "normPool","otherReal")
        .addLayer("actGame1", new DenseLayer.Builder().nIn(hparams.internal_dim)
          .nOut(hparams.internal_dim).activation(Activation.RELU).build(), "combinedGame")
        .addLayer("normGame1", new BatchNormalization.Builder().nIn(hparams.internal_dim).nOut(hparams.internal_dim).build(), "actGame1")
        .addVertex("repeatGame", new ReshapeVertex(-1,hparams.internal_dim,1), "normGame1")
        .addLayer("embedAction",new EmbeddingSequenceLayer.Builder().nIn(hparams.max_represents)
          .nOut(hparams.internal_dim/hparams.input_seqlen).inputLength(hparams.model_inputlen).build(),"actionIDs")
        .addVertex("refold", new ReshapeVertex(-1,hparams.internal_dim,hparams.max_representable_actions), "embedAction") 
        .addVertex("combined", new ElementWiseVertex(Op.Product), "refold","repeatGame")   
        .addLayer("actlin1", new Convolution1D.Builder().nIn(hparams.internal_dim).nOut(hparams.internal_dim)
          .activation(Activation.RELU).kernelSize(1).build(), "combined")
        .addLayer("lastlin", new Convolution1D.Builder().nIn(hparams.internal_dim).nOut(1).kernelSize(1)
          .activation(Activation.TANH).build(), "actlin1")
        .addVertex("maskout", new ElementWiseVertex(Op.Product), "lastlin","mask")
        .addLayer("linout",new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY).nIn(1).nOut(1).build(),"maskout")
        
        .setOutputs("linout")   
        .build();

    ComputationGraph net = new ComputationGraph(conf);
    losses=new CollectScoresIterationListener(1);
    net.setListeners(losses);
    net.init();
    return net;
    }
    //Ends the current game, no more experiences should be 
    //added after this
    public void endGame(Player player,String winner){
        getCurrentGame().setWinner(player,winner);
        if(games.size()>hparams.games_to_keep){
            games.removeFirst();
        }
    }
    public GameSequence getCurrentGame(){
        return games.getLast();
    }

    
    
}
