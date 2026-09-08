package com.socialapp.story.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One piece of text drawn on top of a story's media. Position is fractional
  * (0..1 of the media's width/height) so it renders correctly at any screen
  * size, matching how the story was composed. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextOverlay {

    private String text;

    private String fontFamily;

    /** Hex color, e.g. "#FFFFFF". */
    private String color;

    private double x;

    private double y;

    private double fontSize;
}
