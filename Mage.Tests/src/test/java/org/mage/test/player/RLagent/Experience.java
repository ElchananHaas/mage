package org.mage.test.player.RLagent;

import mage.abilities.*;
import mage.abilities.common.PassAbility;
import mage.abilities.costs.mana.GenericManaCost;
import mage.cards.Card;
import mage.cards.Cards;
import mage.choices.Choice;
import mage.constants.Outcome;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.combat.CombatGroup;
import mage.game.events.GameEvent;
import mage.game.permanent.Permanent;
import mage.game.stack.StackAbility;
import mage.player.ai.ComputerPlayer;
import mage.players.Player;
import mage.target.Target;
import mage.target.TargetAmount;
import mage.target.TargetCard;
import mage.util.RandomUtil;

import java.io.Serializable;
import java.util.*;
import org.apache.log4j.Logger;
import org.mage.test.player.RLagent.*;

/**
 * @author Elchanan Haas
 */

public class Experience{
    Game game;
    List<RLAction> actions;
    int chosen;
    public Experience(Game inGame, List<RLAction> inActions, int inChoice){
        game=inGame;
        actions=inActions;
        chosen=inChoice;
    }
}