package com.EduePoa.EP.FeeComponents.Requests;

import lombok.Data;

@Data

public class FeeComponentRequest {

    private String name;
    private String description;
    /** Component type. Use "OPTIONAL" to mark it as an optional fee; "MANDATORY" otherwise. */
    private String type;
    private String category;
    /** Whether a parent may self-assign this optional component. Defaults false when omitted. */
    private Boolean parentAssignable;
}
