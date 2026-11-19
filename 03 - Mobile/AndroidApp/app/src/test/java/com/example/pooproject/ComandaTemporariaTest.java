package com.example.pooproject;

import java.math.BigDecimal;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComandaTemporariaTest {
    @Test public void limitsQuantityAndPreservesExactCents() {
        var product = new ProdutoMenu(1,"Chá",new BigDecimal("0.10"),"Bebidas","","");
        var draft = new ComandaTemporaria();
        draft.adjust(product,-1);
        assertEquals(0,draft.quantity(1));
        draft.adjust(product,3);
        assertEquals(new BigDecimal("0.30"),draft.total());
        draft.adjust(product,100);
        assertEquals(99,draft.quantity(1));
        draft.adjust(product,-99);
        assertTrue(draft.items().isEmpty());
    }

    @Test public void reconcilesPriceAndUnavailableProducts() {
        var original = new ProdutoMenu(2,"Chá",new BigDecimal("3.99"),"Bebidas","","");
        var update = new ProdutoMenu(2,"Chá",new BigDecimal("4.20"),"Bebidas","","");
        var draft = new ComandaTemporaria();
        draft.adjust(original,2);
        draft.update(Collections.singletonList(update));
        assertEquals(new BigDecimal("8.40"),draft.total());
        draft.update(Collections.emptyList());
        assertTrue(draft.items().isEmpty());
        assertEquals(BigDecimal.ZERO,draft.total());
    }
}
