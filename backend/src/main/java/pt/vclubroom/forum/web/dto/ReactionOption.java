package pt.vclubroom.forum.web.dto;

/** One choice of the fixed set: the code the API takes ("party") and the emoji to show ("🎉"). */
public record ReactionOption(String code, String emoji) {
}
