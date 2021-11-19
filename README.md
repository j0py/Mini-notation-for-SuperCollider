# Tidal-syntax-for-SClang
Use Tidal syntax to play with NodeProxy objects.

When exploring Tidal i found the short syntax quite nice to use.

This project tries to create that in SClang.

The basic idea is this:

```
NP(~a).sound("bd:2 hh:1 cl:1 sn:7").play(0.7).mon(0.5);
```

The NP class is given a NodeProxy, and has, possibly many, chained methods.
These methods will operate on the NodeProxy.

NP will initialize the NodeProxy with:
```
proxy.ar(2);
proxy.source = { Silent.ar()!2 };
```

The ```sound()``` method is given a specification which sounds to play.  
This specification is parsed and then stored in the NP class.
The specification above specifies 4 steps in a sequence.  
The first step should play a "bd" sound.  
If there is a map ```samples/bd``` available, then the third sample file found there will be played (using a built-in Synthdef which can play a sample file).  
If ```samples/bd``` is not available, then a Synthdef added as ```\bd``` will be played.

The structure / timing for the Pbind can also be derived from the given specification. The specification above shows us 4 steps (notes) to play, so the resulting durations should be ```[1/4, 1/4, 1/4, 1/4]``` used in a Pseq in the Pbind.

The ```play()``` method will create a Pbind on the NodeProxy, using the information that has been stored inside the NP class by other methods that were called before ```play()``` was called.

The parameter to ```play()``` is the volume with which the Pbind should play on the NodeProxy private bus.

The Pbind is added by NP on slot 10, but of course this number should be a parameter, so that you can add multiple Pbinds to a proxy using multiple NP objects that operate on one proxy.

The ```mon()``` method will call ```play``` on the proxy, and the parameter to ```mon()``` is the monitor volume to use.

This is the basic idea.  
The parsing of the specification supports the mininotation ```[ ]``` nesting and one by one all the mininotation capabilities could be added.

Other methods controlling the ```number()``` or ```amp()``` can also have a mininotation specification as parameter.  
With ```amp()``` you can then play accents or ghost notes.

In Tidal you can say which of the given specifications finally determines the 'structure', that means the durations for the Pbind. In NP i will make two versions for each method: one regular, and one prefixed with an ```_``` character. If you call ```_sound()``` then the given specification will be used to create the structure for Pbind.

The rule will be that the _last_ method for which the ```_``` version was called will determine the structure.

So far the description and ideas for this project.
