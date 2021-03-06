package mage.cards.a;

import mage.abilities.effects.common.ReturnFromGraveyardToBattlefieldTargetEffect;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.filter.common.FilterArtifactCard;
import mage.target.common.TargetCardInYourGraveyard;

import java.util.UUID;

/**
 * @author North
 */
public final class ArgivianRestoration extends CardImpl {

    public ArgivianRestoration(UUID ownerId, CardSetInfo setInfo) {
        super(ownerId, setInfo, new CardType[]{CardType.SORCERY}, "{2}{U}{U}");

        // Return target artifact card from your graveyard to the battlefield.
        this.getSpellAbility().addEffect(new ReturnFromGraveyardToBattlefieldTargetEffect(false, false));
        this.getSpellAbility().addTarget(new TargetCardInYourGraveyard(new FilterArtifactCard("artifact card from your graveyard")));
    }

    public ArgivianRestoration(final ArgivianRestoration card) {
        super(card);
    }

    @Override
    public ArgivianRestoration copy() {
        return new ArgivianRestoration(this);
    }
}
