# Tidal-syntax-for-SClang
Use Tidal syntax to play with NodeProxy objects

When exploring Tidal i found the short syntax quite nice to use.

This project tries to create that in SClang.

The basic idea is this:

```
NP(~a).sound("bd:2 hh:1 cl:1 sn:7").play(0.7).mon(0.5);
```

The NP class is given a NodeProxy, and i create methods that you can call on that class.
These methods will do things with the NodeProxy.

The ```sound()``` method, for example, is given a specification which sounds to play. This specification is parsed and then stored in the NP class.

The ```play()``` method will create a Pbind on the NodeProxy, and this Pbind will use a built-in synthdef for playing a sample. The samples are searched insize a "samples" map, in which the "bd" map contains various bassdrum samples for instance.
So what samples to play, and also the durations are both derived from the specification that was given with the ```sound()``` method.  
The sequence of calling method is, for now, relevant: do not call ```play``` before calling ```sound```.

The parameter to the ```play()``` method is the volume with which the Pbind should play on the NodeProxy private bus.

The NodeProxy is initialized with:
```
proxy.ar(2);
proxy.source = { Silent.ar()!2 };
```
And then the Pbind is added by NP on slot 10, but of course this number should be a parameter, so that you can ann multiple Pbinds to a proxy using multiple NP objects that operate on one proxy.

The ```mon()``` method will call ```play``` on the proxy, and the parameter to ```mon()``` is the monitor volume to use.

That is the basic idea. The parsing of the specification supports the mininotation ```[ ]``` nesting and one by one all the mininotation capabilities could be added.

Other methods controlling the ```number()``` or ```amp()``` can also have a mininotation specification as parameter. With ```amp()``` you can then play accents or ghost notes.

In Tidal you can say which of the given specifications finally determines the 'structure', that means the durations for the Pbind. In NP i will make two versions for each method: one regular, and one prefixed with an ```_``` character. If you call ```_sound()``` then the given specification will be used to create the structure for Pbind.

The rule will be that the _last_ method for which the ```_``` version was called will determine the structure.

So far the description and ideas for this project, there will be things changed or added to this text as the development goes along.


