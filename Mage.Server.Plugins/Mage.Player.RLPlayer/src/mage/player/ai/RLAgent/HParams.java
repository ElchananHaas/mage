package mage.player.ai.RLAgent;

public class HParams {
    //These are fixed named actions, counter 
    //start sould be 1 beyond these 
    public final static int no_attack=1;
    public final static int no_block=2;
    public final static int yes_mulligan=3;
    public final static int no_mulligan=4;
    public final static int counter_start=5;

    public final static int max_represents=256; 
    public final static int input_seqlen=2;
    public final static int max_representable_actions=16;
    public final static int max_representable_permanents=32; 
    public final static int model_inputlen=max_representable_actions*input_seqlen;
    public final static int internal_dim=32; //For speed for now, should be good enough
    public final static int game_reals=4;
    public final static int num_game_reprs=2;
    public final static int total_model_inputs=3;
    public final static double discount=.99;
    public final static int games_to_keep=1000;
}
