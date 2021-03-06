
package mage.cards.c;

import java.util.UUID;
import mage.MageInt;
import mage.ObjectColor;
import mage.abilities.Ability;
import mage.abilities.common.EntersBattlefieldAllTriggeredAbility;
import mage.abilities.effects.common.TapTargetEffect;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.constants.SubType;
import mage.constants.TargetController;
import mage.constants.Zone;
import mage.filter.FilterPermanent;
import mage.filter.common.FilterCreaturePermanent;
import mage.filter.predicate.mageobject.ColorPredicate;
import mage.filter.predicate.permanent.AnotherPredicate;
import mage.target.common.TargetCreaturePermanent;

/**
 *
 * @author LevelX2
 */
public final class CourtStreetDenizen extends CardImpl {

    private static final FilterPermanent filter = new FilterCreaturePermanent("another white creature");
    private static final FilterCreaturePermanent filterOpponentCreature = new FilterCreaturePermanent("creature an opponent controls");
    static {
        filter.add(AnotherPredicate.instance);
        filter.add(new ColorPredicate(ObjectColor.WHITE));
        filter.add(TargetController.YOU.getControllerPredicate());
        filterOpponentCreature.add(TargetController.OPPONENT.getControllerPredicate());
    }
    public CourtStreetDenizen(UUID ownerId, CardSetInfo setInfo) {
        super(ownerId,setInfo,new CardType[]{CardType.CREATURE},"{2}{W}");
        this.subtype.add(SubType.HUMAN);
        this.subtype.add(SubType.SOLDIER);

        this.power = new MageInt(2);
        this.toughness = new MageInt(2);

        // Whenever another white creature enters the battlefield under your control, tap target creature an opponent controls.
        Ability ability = new EntersBattlefieldAllTriggeredAbility(Zone.BATTLEFIELD, new TapTargetEffect(),filter,false,null, true);
        ability.addTarget(new TargetCreaturePermanent(filterOpponentCreature));
        this.addAbility(ability);
    }

    public CourtStreetDenizen(final CourtStreetDenizen card) {
        super(card);
    }

    @Override
    public CourtStreetDenizen copy() {
        return new CourtStreetDenizen(this);
    }
}
