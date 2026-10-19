/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Builds isolated dependencies for the adjacent manual service tests.
 */

package com.gcmb.backend.manual.administration.pricing.service;

import com.gcmb.backend.administration.pricing.dto.PriceCatalogItem;
import com.gcmb.backend.administration.pricing.dto.PriceRuleView;
import com.gcmb.backend.administration.pricing.dto.RoomTypeView;
import com.gcmb.backend.administration.pricing.service.RoomCatalogService;
import com.gcmb.backend.manual.support.ManualFixture;
import com.gcmb.backend.manual.support.ManualJdbc;
import java.time.LocalDate;
import java.util.List;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.*;

public final class RoomCatalogFixture extends ManualFixture {
    public final RoomCatalogService service;

    public RoomCatalogFixture(Object command) {
        super(command);
        ManualJdbc.attach(this);
        f.put("roomTypeView",new RoomTypeView(RT,"QUEEN_01","Queen","CINEMA",1,"",true));
        f.put("priceView",new PriceCatalogItem(C,"COMBO","COMBO_A",RT,"Queen","Gói A","Mô tả",120,420,1380,money("200000"),true));
        f.put("priceRuleView",new PriceRuleView(PR,"PRICE_A",1,RT,"Queen","Giá A",List.of(1),LocalDate.of(2026,10,20),null,420,1380,money("120000"),true));
        service=new RoomCatalogService(db);
    }
}
