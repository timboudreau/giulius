package com.mastfrog.test.c;

import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;

/**
 *
 * @author Tim Boudreau
 */
@Defaults({"one=1", "two=2", "three=3", "fromC=see", "bubbleBunny=pwadge"})
public class Nothing {
    
    @Defaults(namespace = @Namespace("foo"), value= {"x=y", "foo-one=1c", "gak=boo"})
    public class A {
        
    }

    @Defaults(namespace = @Namespace("bar"), value= {"x=y", "bar-one=1c"})
    public class B {
        
    }
    
    @Defaults(namespace = @Namespace("baz"), value= {"x=y", "baz-two=2c"})
    public class C {
        
    }    
}
