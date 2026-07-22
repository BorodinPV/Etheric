package com.etheric.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwkKey {

    private String kty;
    private String kid;
    private String use;
    private String alg;
    private String n;
    private String e;
}
