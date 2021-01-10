package mage.player.ai.RLAgent;

import org.nd4j.linalg.api.ndarray.INDArray;
import mage.abilities.*;
import mage.MageObject;
import mage.abilities.common.PassAbility;
import mage.game.Game;
import mage.game.permanent.Permanent;
import mage.players.Player;
import java.io.Serializable;
import java.util.*;
import org.apache.log4j.Logger;
import org.nd4j.linalg.factory.Nd4j;
import mage.game.permanent.Battlefield;
import com.google.gson.*;

public class RepresentedGame implements Serializable{
    protected int numActions;
    protected List<INDArray> actionRepr;
    protected List<INDArray> gameRepr;
    protected boolean isDummy;
    protected float agentLife;
    protected float opponentLife;
    RepresentedGame(int numActions, List<INDArray> gameRepr,List<INDArray> actionRepr,float agentLife,float opponentLife){
        this(numActions, gameRepr,actionRepr,agentLife,opponentLife,false);
    }
    RepresentedGame(int numActions, List<INDArray> gameRepr,List<INDArray> actionRepr,float agentLife,float opponentLife, boolean isDummy){
        this.numActions=numActions;
        this.gameRepr=gameRepr;
        this.actionRepr=actionRepr;
        this.isDummy=isDummy;
        this.agentLife=agentLife;
        this.opponentLife=opponentLife;
    }
    String asJsonString(){
        Gson gson = new Gson();
        return gson.toJson(this);
    }
    //returns the data prepared to be fed into the model
    public INDArray[] asModelInputs(){
        HParams hparams=new HParams();
        INDArray inputs[]=new INDArray[hparams.total_model_inputs];
        inputs[0]=Nd4j.expandDims(actionRepr.get(0), 0);
        for(int i=0;i<gameRepr.size();i++){
            inputs[i+1]=Nd4j.expandDims(gameRepr.get(i), 0);
        }
        return inputs;
    }
    public float getAgentLife(){
        return agentLife;
    }
    public float getOpponentLife(){
        return opponentLife;
    }
    public int getNumActions(){
        return numActions;
    }
}
