package com.mastfrog.test.b;

import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;

/**
 *
 * @author Tim Boudreau
 */
@Defaults({"one=b1", "two=b2", "three=b3", "four=4"})
public class Nothing {
    
    @Defaults(namespace = @Namespace("foo"), value= {"x=b", "foo-one=1b", "foo-two=2b"})
    public class A {
        
    }

    @Defaults(namespace = @Namespace("bar"), value= {"x=b", "bar-one=1", "hoopy=frood"})
    public class B {
        
    }
    
    @Defaults(namespace = @Namespace("baz"), value= {"x=y", "baz-two=2b"})
    public class C {
        
    }    
}
