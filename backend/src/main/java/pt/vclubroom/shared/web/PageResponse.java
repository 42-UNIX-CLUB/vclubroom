package pt.vclubroom.shared.web;

import java.util.List;

/** Offset page for admin lists and the directory. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {
}
