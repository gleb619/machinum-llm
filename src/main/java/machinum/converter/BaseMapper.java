package machinum.converter;

import org.mapstruct.Mapper;

import java.util.List;

public interface BaseMapper<E, D> {

    List<E> toEntity(List<D> list);

    List<D> toDto(List<E> value);

    E toEntity(D value);

    D toDto(E value);

}
