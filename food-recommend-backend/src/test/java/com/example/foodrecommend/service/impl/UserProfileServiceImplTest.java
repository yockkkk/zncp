package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.dto.GuestProfile;
import com.example.foodrecommend.dto.TagInputDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationFeedbackMapper;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.DishService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserProfileServiceImpl.
 *
 * Adaptations:
 * - Real impl uses @Autowired (field injection), not @RequiredArgsConstructor.
 *   MockitoExtension still injects @Mock fields by type into @InjectMocks.
 * - ObjectMapper is @Autowired — we inject real instance via @Spy would be cleaner,
 *   but @InjectMocks will inject the @Mock one. We supply a real one via field setter.
 * - getCustomerHistoryProfile is called internally when phone is set — mocking
 *   recordMapper to return empty list simulates "new customer".
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private RecommendationRecordMapper recordMapper;

    @Mock
    private RecommendationFeedbackMapper feedbackMapper;

    @Mock
    private DishService dishService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserProfileServiceImpl service;

    @Test
    void singleGuest_basicFields_populated() {
        TagInputDTO tags = new TagInputDTO();
        tags.setPeopleCount("2");
        tags.setDiningScene("约会");
        tags.setBudgetLevel("中等");
        tags.setTastePreferences(List.of("辣"));

        UserProfileDTO profile = service.buildProfile(tags, null);

        assertThat(profile.getPeopleCount()).isEqualTo(2);
        assertThat(profile.getDiningScene()).isEqualTo("约会");
        assertThat(profile.getEstimatedConsumptionLevel()).isEqualTo("中等");
        assertThat(profile.getPossiblePreferences()).contains("辣");
    }

    @Test
    void singleGuest_withDietaryRestriction_addedToLifestyles() {
        TagInputDTO tags = new TagInputDTO();
        tags.setPeopleCount("1");
        tags.setBudgetLevel("实惠");
        tags.setDietaryRestriction("素食");

        UserProfileDTO profile = service.buildProfile(tags, null);

        assertThat(profile.getConsolidatedDietLifestyles()).contains("素食");
        assertThat(profile.getHealthGoal()).isEqualTo("健康素食");
    }

    @Test
    void multiGuest_consolidatesRestrictionsFromAllGuests() {
        GuestProfile guestA = new GuestProfile();
        guestA.setName("顾客A");
        guestA.setAvoidIngredients(List.of("辣"));
        guestA.setTastes(List.of("清淡"));

        GuestProfile guestB = new GuestProfile();
        guestB.setName("顾客B");
        guestB.setAllergens(List.of("海鲜"));
        guestB.setDietLifestyles(List.of("清真"));

        TagInputDTO tags = new TagInputDTO();
        tags.setGuests(List.of(guestA, guestB));
        tags.setBudgetLevel("中等");

        UserProfileDTO profile = service.buildProfile(tags, null);

        assertThat(profile.getGuests()).hasSize(2);
        assertThat(profile.getPeopleCount()).isEqualTo(2);
        assertThat(profile.getConsolidatedAvoids()).contains("辣");
        assertThat(profile.getConsolidatedAllergens()).contains("海鲜");
        assertThat(profile.getConsolidatedDietLifestyles()).contains("清真");
        assertThat(profile.getPossiblePreferences()).contains("清淡");
    }

    @Test
    void singleGuest_withPhone_lookupHistoryInvoked() {
        when(recordMapper.selectList(any())).thenReturn(List.of());

        TagInputDTO tags = new TagInputDTO();
        tags.setPeopleCount("1");
        tags.setBudgetLevel("中等");
        tags.setPhone("13800138000");

        UserProfileDTO profile = service.buildProfile(tags, null);

        // recordMapper must have been called for history lookup
        verify(recordMapper).selectList(any());
        assertThat(profile.getPhone()).isEqualTo("13800138000");
        // New customer → historyDescription contains "新顾客"
        assertThat(profile.getHistoryDescription()).contains("新顾客");
    }

    @Test
    void singleGuest_withoutPhone_noHistoryLookup() {
        TagInputDTO tags = new TagInputDTO();
        tags.setPeopleCount("1");
        tags.setBudgetLevel("中等");
        // no phone set

        UserProfileDTO profile = service.buildProfile(tags, null);

        verify(recordMapper, never()).selectList(any());
        assertThat(profile).isNotNull();
        assertThat(profile.getPeopleCount()).isEqualTo(1);
    }

    @Test
    void getCustomerHistoryProfile_noRecords_returnsNewCustomerDescription() {
        when(recordMapper.selectList(any())).thenReturn(List.of());

        UserProfileDTO profile = service.getCustomerHistoryProfile("13900139000");

        assertThat(profile.getHistoryDescription()).contains("新顾客");
        assertThat(profile.getHistoryTastes()).isEmpty();
    }
}
