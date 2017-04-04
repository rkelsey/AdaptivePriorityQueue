/**
 * A Tuple to store two objects together
 * @author Ryan Kelsey and Lee Berman
 *
 * @param <A> The type of the first object
 * @param <B> The type of the second object
 */
public class Tuple<A, B> {
	public A a;
	public B b;
	
	public Tuple(A a, B b) {
		this.a = a;
		this.b = b;
	}
}