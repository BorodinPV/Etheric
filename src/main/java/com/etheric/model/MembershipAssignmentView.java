package com.etheric.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipAssignmentView {

    private String id;
    private String label;
    private boolean assigned;
}
