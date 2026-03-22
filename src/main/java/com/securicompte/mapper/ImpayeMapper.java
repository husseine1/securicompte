package com.securicompte.mapper;

import com.securicompte.dto.ImpayeDto;
import com.securicompte.entity.Impaye;
import org.mapstruct.*;

/**
 * Mapper MapStruct : Impaye → ImpayeDto.
 * Utilisé via ClientService.toImpayeDto() et ImpayeService.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ImpayeMapper {

    @Mapping(source = "client.id",            target = "clientId")
    @Mapping(source = "client.numeroClient",  target = "numeroClient")
    @Mapping(source = "client.nom",           target = "nomClient")
    @Mapping(source = "souscription.securicompte", target = "securicompte")
    @Mapping(source = "regularisePar.username",    target = "regularisePar")
    @Mapping(target = "moisNom", expression = "java(getMoisNom(impaye.getMois()))")
    ImpayeDto toDto(Impaye impaye);

    default String getMoisNom(Integer mois) {
        String[] noms = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                         "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
        return (mois != null && mois >= 1 && mois <= 12) ? noms[mois] : "";
    }
}
