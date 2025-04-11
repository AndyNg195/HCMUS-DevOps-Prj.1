package org.springframework.samples.petclinic.visits.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;


import static java.util.Arrays.asList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


@ExtendWith(SpringExtension.class)
@WebMvcTest(VisitResource.class)
@ActiveProfiles("test")
class VisitResourceTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    VisitRepository visitRepository;

    @Test
    void shouldFetchVisits() throws Exception {
        given(visitRepository.findByPetIdIn(asList(111, 222)))
            .willReturn(
                asList(
                    Visit.VisitBuilder.aVisit()
                        .id(1)
                        .petId(111)
                        .build(),
                    Visit.VisitBuilder.aVisit()
                        .id(2)
                        .petId(222)
                        .build(),
                    Visit.VisitBuilder.aVisit()
                        .id(3)
                        .petId(222)
                        .build()
                )
            );

        mvc.perform(get("/pets/visits?petId=111,222"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(1))
            .andExpect(jsonPath("$.items[1].id").value(2))
            .andExpect(jsonPath("$.items[2].id").value(3))
            .andExpect(jsonPath("$.items[0].petId").value(111))
            .andExpect(jsonPath("$.items[1].petId").value(222))
            .andExpect(jsonPath("$.items[2].petId").value(222));
    }

    @Test
    void shouldCreateVisitSuccessfully() throws Exception {
        Visit visit = new Visit();
        visit.setDescription("Regular check-up");

        when(visitRepository.save(any(Visit.class))).thenReturn(visit);

        mvc.perform(post("/owners/*/pets/1/visits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Regular check-up\"}"))
                .andExpect(status().isCreated());

        verify(visitRepository, times(1)).save(any(Visit.class));
    }

    @Test
    void shouldFetchVisitsForPetId() throws Exception {
        Visit visit = new Visit();
        visit.setId(1);
        visit.setPetId(1);
        visit.setDescription("Regular check-up");

        when(visitRepository.findByPetId(1)).thenReturn(Collections.singletonList(visit));

        mvc.perform(get("/owners/*/pets/1/visits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].description").value("Regular check-up"));
    }

    @Test
    void shouldReturnEmptyListWhenNoVisitsForPetId() throws Exception {
        when(visitRepository.findByPetId(1)).thenReturn(Collections.emptyList());

        mvc.perform(get("/owners/*/pets/1/visits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldFetchVisitsForMultiplePetIds() throws Exception {
        Visit visit1 = new Visit();
        visit1.setId(1);
        visit1.setPetId(1);
        visit1.setDescription("Visit 1");

        Visit visit2 = new Visit();
        visit2.setId(2);
        visit2.setPetId(2);
        visit2.setDescription("Visit 2");

        List<Integer> petIds = Arrays.asList(1, 2);

        when(visitRepository.findByPetIdIn(petIds)).thenReturn(Arrays.asList(visit1, visit2));

        mvc.perform(get("/pets/visits?petId=1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[1].id").value(2));
    }
}