package de.intranda.goobi.plugins;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JsonField {

    // name of the json field
    private String name;

    // type: identifier, metadata, filename, extension, static
    private String type;

    // value to use - e.g. name of the metadata field or a static text
    private String value;

}
